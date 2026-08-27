// admin-embed-authorities
//
// POST { limit?, batch_size?, re_embed? } -> { embedded, failed, remaining, model }
//
// Backfills embeddings for authority_chunks. Until this existed, the
// vector(1536) column and its HNSW index were dead weight: chunks went in
// with embedding=null and nothing ever populated them, so retrieval was
// keyword-only no matter what the schema implied.
//
// Deliberately incremental rather than "embed everything": Edge Functions
// have a wall-clock limit, and a real corpus is tens of thousands of
// chunks. Each call does a bounded amount of work and reports how many
// remain, so the caller loops until remaining hits 0. That also means a
// failure midway costs one batch, not the whole run.
//
// re_embed=true re-embeds chunks whose embedding_model differs from the
// current model. Vectors from different models are not comparable — mixing
// them silently corrupts similarity ranking — so a model switch has to be
// a deliberate, resumable re-run, which is why embedding_model is stored
// per chunk rather than assumed globally.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, requireCapability, logAction, serviceClient, json, badRequest, corsPreflight } from '../_shared/admin.ts';

const GEMINI_API_KEY = Deno.env.get('GEMINI_API_KEY');
const EMBED_MODEL = Deno.env.get('GEMINI_EMBED_MODEL') || 'gemini-embedding-001';

// Must match the vector(1536) column. gemini-embedding-001 defaults to
// 3072 and supports Matryoshka truncation down to 1536, which is why this
// project can keep the existing column width instead of migrating it.
const EMBED_DIMS = 1536;

// gemini-embedding-001 caps input at ~2048 tokens. Long articles are
// truncated FOR EMBEDDING ONLY — the full chunk_text is untouched in the
// database and is what actually gets handed to the model at answer time.
// A truncated vector costs some retrieval precision on very long chunks;
// silently dropping them from the index would cost far more.
const MAX_EMBED_CHARS = 6000;

const DEFAULT_LIMIT = 200;
const DEFAULT_BATCH = 32;

type Chunk = { id: string; chunk_text: string };

// Cosine distance is scale-invariant so this doesn't change today's
// ranking, but truncated Matryoshka vectors come back un-normalized and
// any future switch to L2/inner-product would silently misrank without it.
function normalize(v: number[]): number[] {
  let sum = 0;
  for (const x of v) sum += x * x;
  const norm = Math.sqrt(sum);
  return norm > 0 ? v.map((x) => x / norm) : v;
}

async function embedBatch(texts: string[]): Promise<number[][]> {
  const resp = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${EMBED_MODEL}:batchEmbedContents?key=${GEMINI_API_KEY}`,
    {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        requests: texts.map((t) => ({
          model: `models/${EMBED_MODEL}`,
          content: { parts: [{ text: t.slice(0, MAX_EMBED_CHARS) }] },
          // RETRIEVAL_DOCUMENT (vs RETRIEVAL_QUERY at search time) puts
          // stored passages and incoming questions into the asymmetric
          // embedding space the model was trained for.
          taskType: 'RETRIEVAL_DOCUMENT',
          outputDimensionality: EMBED_DIMS,
        })),
      }),
    },
  );

  if (!resp.ok) {
    const body = await resp.text().catch(() => '');
    throw new Error(`Gemini embed API returned ${resp.status}: ${body.slice(0, 300)}`);
  }

  const payload = await resp.json();
  const embeddings = payload?.embeddings;
  if (!Array.isArray(embeddings) || embeddings.length !== texts.length) {
    throw new Error(`Gemini returned ${embeddings?.length ?? 0} embeddings for ${texts.length} inputs`);
  }

  return embeddings.map((e: { values?: number[] }) => {
    const values = e?.values;
    if (!Array.isArray(values) || values.length !== EMBED_DIMS) {
      throw new Error(`expected ${EMBED_DIMS}-dim embedding, got ${values?.length ?? 'none'}`);
    }
    return normalize(values);
  });
}

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId, asUser } = caller;
  const asService = serviceClient();

  const capError = await requireCapability(asUser, asService, callerId, 'manage_corpus', 'authority_chunks_embedded');
  if (capError) return capError;

  if (!GEMINI_API_KEY) {
    return json({ error: 'GEMINI_API_KEY is not configured — cannot generate embeddings' }, 502);
  }

  const body = await req.json().catch(() => ({}));
  const limit = Math.min(Math.max(Number(body?.limit) || DEFAULT_LIMIT, 1), 1000);
  const batchSize = Math.min(Math.max(Number(body?.batch_size) || DEFAULT_BATCH, 1), 100);
  const reEmbed = body?.re_embed === true;

  const selection = asService.from('authority_chunks').select('id, chunk_text').limit(limit);
  const { data: chunks, error: fetchError } = reEmbed
    ? await selection.or(`embedding.is.null,embedding_model.neq.${EMBED_MODEL}`)
    : await selection.is('embedding', null);

  if (fetchError) return json({ error: fetchError.message }, 500);

  const pending = (chunks ?? []) as Chunk[];
  if (pending.length === 0) {
    return json({ embedded: 0, failed: 0, remaining: 0, model: EMBED_MODEL, note: 'nothing to embed' });
  }

  let embedded = 0;
  const failures: { batch_start: number; error: string }[] = [];

  for (let i = 0; i < pending.length; i += batchSize) {
    const batch = pending.slice(i, i + batchSize);
    let vectors: number[][];
    try {
      vectors = await embedBatch(batch.map((c) => c.chunk_text));
    } catch (e) {
      // One bad batch shouldn't abort the run — record it and keep going,
      // since those chunks simply stay unembedded and get picked up next call.
      failures.push({ batch_start: i, error: e instanceof Error ? e.message : String(e) });
      continue;
    }

    const embeddedAt = new Date().toISOString();
    for (let j = 0; j < batch.length; j++) {
      const { error: updateError } = await asService
        .from('authority_chunks')
        .update({
          embedding: JSON.stringify(vectors[j]),
          embedding_model: EMBED_MODEL,
          embedded_at: embeddedAt,
        })
        .eq('id', batch[j].id);
      if (updateError) {
        failures.push({ batch_start: i + j, error: updateError.message });
      } else {
        embedded++;
      }
    }
  }

  const { count: remaining } = await asService
    .from('authority_chunks')
    .select('id', { count: 'exact', head: true })
    .is('embedding', null);

  await logAction(asService, callerId, 'authority_chunks_embedded', {
    targetTable: 'authority_chunks',
    after: { embedded, failed: failures.length, model: EMBED_MODEL, re_embed: reEmbed },
    success: failures.length === 0,
  });

  return json({
    embedded,
    failed: failures.length,
    failures: failures.slice(0, 10),
    remaining: remaining ?? 0,
    model: EMBED_MODEL,
    note: (remaining ?? 0) > 0 ? 'call again to continue — work is bounded per invocation' : 'all chunks embedded',
  });
});
