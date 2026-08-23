import { createClient } from '@/lib/supabase/server';
import { Card, Badge } from '@/components/ui';
import { PublishPromptForm } from './publish-prompt-form';
import { RollbackButton } from './rollback-button';

type PromptVersionRow = {
  id: string;
  prompt_key: string;
  content: string;
  version_label: string;
  status: 'published' | 'archived';
  created_by: string | null;
  published_by: string | null;
  published_at: string | null;
};

export default async function PromptVersionsPage() {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from('prompt_versions')
    .select('*')
    .order('published_at', { ascending: false, nullsFirst: false });

  const versions = (data ?? []) as PromptVersionRow[];

  const byKey = new Map<string, PromptVersionRow[]>();
  for (const v of versions) {
    const list = byKey.get(v.prompt_key) ?? [];
    list.push(v);
    byKey.set(v.prompt_key, list);
  }

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Prompt versions</h1>
        <p className="mt-1 text-sm text-ink-2">
          Prompt content is immutable once published — rolling back clones an old version&apos;s
          content into a new row rather than resurrecting it.
        </p>
      </div>

      <Card title="Publish new version">
        <PublishPromptForm />
      </Card>

      <Card title="Version history">
        <p className="mb-4 text-xs text-ink-3">
          Rollback creates a new version cloned from the selected one&apos;s content — it doesn&apos;t
          resurrect the old row.
        </p>
        {error && <p className="text-sm text-risk">Failed to load prompt versions: {error.message}</p>}
        {!error && byKey.size === 0 && <p className="text-sm text-ink-3">No prompt versions published yet.</p>}
        <div className="space-y-6">
          {Array.from(byKey.entries()).map(([key, rows]) => (
            <div key={key}>
              <h3 className="font-mono text-sm font-medium text-ink">{key}</h3>
              <div className="mt-2 space-y-3">
                {rows.map((row) => (
                  <div key={row.id} className="rounded-md border border-rule p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono text-sm text-ink">{row.version_label}</span>
                        {row.status === 'published' ? (
                          <Badge tone="seal">published</Badge>
                        ) : (
                          <Badge tone="default">archived</Badge>
                        )}
                      </div>
                      {row.status === 'archived' && <RollbackButton versionId={row.id} />}
                    </div>
                    <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-ink-3 sm:grid-cols-3">
                      <div>
                        <dt className="inline">created_by: </dt>
                        <dd className="inline font-mono text-ink-2">{row.created_by ?? '—'}</dd>
                      </div>
                      <div>
                        <dt className="inline">published_by: </dt>
                        <dd className="inline font-mono text-ink-2">{row.published_by ?? '—'}</dd>
                      </div>
                      <div>
                        <dt className="inline">published_at: </dt>
                        <dd className="inline font-mono text-ink-2">{row.published_at ?? '—'}</dd>
                      </div>
                    </dl>
                    <pre className="mt-3 max-h-48 overflow-auto whitespace-pre-wrap rounded-md bg-surface-2 p-3 font-mono text-xs text-ink-2">
                      {row.content}
                    </pre>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
