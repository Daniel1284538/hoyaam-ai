import { createClient } from '@/lib/supabase/server';
import { getViewer } from '@/lib/capabilities';
import { Card, Badge } from '@/components/ui';
import { SetPolicyForm } from './set-policy-form';

export type RetentionPolicyRow = {
  id: string;
  matter_type: string;
  retention_years: number;
  basis: string;
  active: boolean;
  created_by: string | null;
  created_at: string;
};

export default async function RetentionPage() {
  const [viewer, supabase] = await Promise.all([getViewer(), createClient()]);
  const canManage = viewer?.capabilities.has('manage_retention') ?? false;

  const { data, error } = await supabase
    .from('retention_policies')
    .select('*')
    .order('matter_type', { ascending: true })
    .order('created_at', { ascending: false });

  const policies = (data ?? []) as RetentionPolicyRow[];

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Retention policy</h1>
        <p className="mt-1 text-sm text-ink-2">
          Setting a new policy for a matter_type deactivates the previous one — it isn&apos;t
          deleted, just superseded.
        </p>
      </div>

      {error && <p className="text-sm text-risk">Failed to load retention policies: {error.message}</p>}

      {canManage && (
        <Card title="Set policy">
          <SetPolicyForm />
        </Card>
      )}

      <Card title="Policies">
        {!error && policies.length === 0 && <p className="text-sm text-ink-3">No retention policies set yet.</p>}
        <ul className="space-y-3">
          {policies.map((policy) => (
            <li key={policy.id} className="rounded-md border border-rule p-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  {policy.active && <Badge tone="seal">Active</Badge>}
                  <span className="font-mono text-xs text-ink">{policy.matter_type}</span>
                  <span className="text-ink-2">{policy.retention_years} years</span>
                </div>
                <span className="text-xs text-ink-3">{new Date(policy.created_at).toLocaleString()}</span>
              </div>
              <p className="mt-2 text-ink-2">{policy.basis}</p>
              <p className="mt-1 text-xs text-ink-3">created_by: {policy.created_by ?? '—'}</p>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
