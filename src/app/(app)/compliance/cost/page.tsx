import { createClient } from '@/lib/supabase/server';
import { Card, Badge } from '@/components/ui';

type SpendLimitRow = {
  id: string;
  scope: 'global' | 'matter_type';
  matter_type: string | null;
  period: string;
  soft_limit_usd: number | null;
  hard_limit_usd: number;
  active: boolean;
  created_by: string | null;
  created_at: string;
};

type UsageLedgerRow = Record<string, unknown> & { id: string };

export default async function CostPage() {
  const supabase = await createClient();

  const [{ data: limitsData, error: limitsError }, { data: usageData, error: usageError }] = await Promise.all([
    supabase.from('spend_limits').select('*'),
    supabase.from('usage_ledger').select('*').limit(50),
  ]);

  const limits = (limitsData ?? []) as SpendLimitRow[];
  const usage = (usageData ?? []) as UsageLedgerRow[];

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Cost &amp; usage</h1>
        <p className="mt-1 text-sm text-ink-2">
          Read-only view. Spend limit setting lives under Agent governance, not here.
        </p>
      </div>

      <Card title="Spend limits">
        {limitsError && <p className="text-sm text-risk">Failed to load spend limits: {limitsError.message}</p>}
        {!limitsError && limits.length === 0 && <p className="text-sm text-ink-3">No spend limits configured yet.</p>}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-rule text-xs uppercase tracking-wide text-ink-3">
                <th className="py-2 pr-4 font-medium">Scope</th>
                <th className="py-2 pr-4 font-medium">Matter type</th>
                <th className="py-2 pr-4 font-medium">Period</th>
                <th className="py-2 pr-4 font-medium">Soft limit</th>
                <th className="py-2 pr-4 font-medium">Hard limit</th>
                <th className="py-2 pr-4 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {limits.map((limit) => (
                <tr key={limit.id} className="border-b border-rule last:border-0">
                  <td className="py-2 pr-4 font-mono text-xs text-ink">{limit.scope}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-2">{limit.matter_type ?? '—'}</td>
                  <td className="py-2 pr-4 text-ink-2">{limit.period}</td>
                  <td className="py-2 pr-4 text-ink-2">
                    {limit.soft_limit_usd != null ? `$${limit.soft_limit_usd}` : '—'}
                  </td>
                  <td className="py-2 pr-4 text-ink-2">${limit.hard_limit_usd}</td>
                  <td className="py-2 pr-4">
                    <Badge tone={limit.active ? 'seal' : 'default'}>{limit.active ? 'active' : 'inactive'}</Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="Usage ledger" action={<span className="text-xs text-ink-3">latest 50</span>}>
        {usageError && <p className="text-sm text-risk">Failed to load usage ledger: {usageError.message}</p>}
        {!usageError && usage.length === 0 && (
          <p className="text-sm text-ink-3">
            No usage recorded yet — there&apos;s no agent runtime in this environment generating it.
          </p>
        )}
        {usage.length > 0 && (
          <pre className="max-h-96 overflow-auto rounded-md bg-surface-2 p-3 font-mono text-xs text-ink-2">
            {JSON.stringify(usage, null, 2)}
          </pre>
        )}
      </Card>
    </div>
  );
}
