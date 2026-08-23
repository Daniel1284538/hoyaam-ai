import { createClient } from '@/lib/supabase/server';
import { Card, Badge } from '@/components/ui';
import { PublishConfigForm } from './publish-config-form';

type AgentConfigRow = {
  id: string;
  config: unknown;
  version_label: string;
  status: 'active' | 'archived';
  created_by: string | null;
  activated_by: string | null;
  activated_at: string | null;
};

export default async function AgentConfigsPage() {
  const supabase = await createClient();
  const { data, error } = await supabase
    .from('agent_configs')
    .select('*')
    .order('activated_at', { ascending: false, nullsFirst: false });

  const configs = (data ?? []) as AgentConfigRow[];

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Agent configs</h1>
        <p className="mt-1 text-sm text-ink-2">
          Model routing, effort, and token-ceiling settings per job type. The shape is deliberately
          not decomposed into columns yet — each version is a raw JSON blob.
        </p>
      </div>

      <Card title="Publish new config">
        <PublishConfigForm />
      </Card>

      <Card title="Version history">
        {error && <p className="text-sm text-risk">Failed to load configs: {error.message}</p>}
        {!error && configs.length === 0 && <p className="text-sm text-ink-3">No configs published yet.</p>}
        <div className="space-y-4">
          {configs.map((row) => (
            <div key={row.id} className="rounded-md border border-rule p-4">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-mono text-sm text-ink">{row.version_label}</span>
                {row.status === 'active' ? (
                  <Badge tone="seal">active</Badge>
                ) : (
                  <Badge tone="default">archived</Badge>
                )}
              </div>
              <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-ink-3 sm:grid-cols-4">
                <div>
                  <dt className="inline">created_by: </dt>
                  <dd className="inline font-mono text-ink-2">{row.created_by ?? '—'}</dd>
                </div>
                <div>
                  <dt className="inline">activated_by: </dt>
                  <dd className="inline font-mono text-ink-2">{row.activated_by ?? '—'}</dd>
                </div>
                <div className="col-span-2">
                  <dt className="inline">activated_at: </dt>
                  <dd className="inline font-mono text-ink-2">{row.activated_at ?? '—'}</dd>
                </div>
              </dl>
              <pre className="mt-3 max-h-64 overflow-auto rounded-md bg-surface-2 p-3 font-mono text-xs text-ink-2">
                {JSON.stringify(row.config, null, 2)}
              </pre>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
