import { createClient } from '@/lib/supabase/server';
import { getViewer } from '@/lib/capabilities';
import { Card } from '@/components/ui';
import { RecordDisclosureForm } from './record-disclosure-form';

export type DisclosureRow = {
  id: string;
  matter_id: string | null;
  client_name: string;
  client_identifier: string;
  addendum_version: string;
  signed_at: string;
  recorded_by: string | null;
  notes: string | null;
  created_at: string;
};

export default async function DisclosuresPage() {
  const [viewer, supabase] = await Promise.all([getViewer(), createClient()]);
  const canManage = viewer?.capabilities.has('manage_disclosures') ?? false;

  const { data, error } = await supabase
    .from('disclosures')
    .select('*')
    .order('signed_at', { ascending: false });

  const disclosures = (data ?? []) as DisclosureRow[];

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Disclosures register</h1>
        <p className="mt-1 text-sm text-ink-2">
          AI-use disclosure addendums signed by clients. No clients table exists yet, so client
          identity here is free text, not a foreign key.
        </p>
      </div>

      {error && <p className="text-sm text-risk">Failed to load disclosures: {error.message}</p>}

      {canManage && (
        <Card title="Record disclosure">
          <RecordDisclosureForm />
        </Card>
      )}

      <Card title="Disclosures">
        {!error && disclosures.length === 0 && <p className="text-sm text-ink-3">No disclosures recorded yet.</p>}
        <ul className="space-y-3">
          {disclosures.map((d) => (
            <li key={d.id} className="rounded-md border border-rule p-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium text-ink">{d.client_name}</span>
                  <span className="font-mono text-xs text-ink-3">{d.client_identifier}</span>
                </div>
                <span className="text-xs text-ink-3">signed {new Date(d.signed_at).toLocaleString()}</span>
              </div>
              <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-ink-2 sm:grid-cols-3">
                <div>
                  <dt className="text-ink-3">Addendum version</dt>
                  <dd className="text-ink">{d.addendum_version}</dd>
                </div>
                <div>
                  <dt className="text-ink-3">Matter</dt>
                  <dd className="font-mono text-ink">{d.matter_id ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-ink-3">Recorded by</dt>
                  <dd className="font-mono text-ink">{d.recorded_by ?? '—'}</dd>
                </div>
              </dl>
              {d.notes && <p className="mt-2 text-ink-2">{d.notes}</p>}
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
