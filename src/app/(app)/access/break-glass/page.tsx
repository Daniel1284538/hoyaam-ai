import { createClient } from '@/lib/supabase/server';
import { Card, Badge } from '@/components/ui';
import { GrantBreakGlassForm } from './grant-break-glass-form';

type BreakGlassRow = {
  id: string;
  matter_id: string;
  user_id: string;
  reason: string;
  granted_at: string;
  expires_at: string;
  revoked_at: string | null;
};

export default async function BreakGlassPage() {
  const supabase = await createClient();
  const { data: rows } = await supabase
    .from('break_glass_grants')
    .select('id, matter_id, user_id, reason, granted_at, expires_at, revoked_at')
    .order('granted_at', { ascending: false });

  const grants = (rows ?? []) as BreakGlassRow[];
  // Server Component: computed once per request render, not memoized
  // client-side state, so reading the clock here is the intended source
  // of "now" for the expired/active badge below.
  // eslint-disable-next-line react-hooks/purity -- see comment above
  const now = Date.now();

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Break-glass access</h1>
        <p className="mt-1 text-sm text-ink-2">
          Emergency, time-boxed access to a matter outside the normal
          grant flow.
        </p>
      </div>

      <Card title="Grants">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-rule text-xs uppercase tracking-wide text-ink-3">
                <th className="py-2 pr-4 font-medium">Matter ID</th>
                <th className="py-2 pr-4 font-medium">User ID</th>
                <th className="py-2 pr-4 font-medium">Reason</th>
                <th className="py-2 pr-4 font-medium">Granted</th>
                <th className="py-2 pr-4 font-medium">Expires</th>
                <th className="py-2 pr-4 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {grants.map((g) => {
                const expired = new Date(g.expires_at).getTime() < now;
                return (
                  <tr key={g.id} className="border-b border-rule last:border-0">
                    <td className="py-2 pr-4 font-mono text-xs text-ink-2">{g.matter_id}</td>
                    <td className="py-2 pr-4 font-mono text-xs text-ink-2">{g.user_id}</td>
                    <td className="py-2 pr-4 text-ink">{g.reason}</td>
                    <td className="py-2 pr-4 font-mono text-xs text-ink-3">{new Date(g.granted_at).toLocaleString()}</td>
                    <td className="py-2 pr-4 font-mono text-xs text-ink-3">{new Date(g.expires_at).toLocaleString()}</td>
                    <td className="py-2 pr-4">
                      {g.revoked_at ? (
                        <Badge tone="risk">revoked</Badge>
                      ) : expired ? (
                        <Badge tone="default">expired</Badge>
                      ) : (
                        <Badge tone="warn">active</Badge>
                      )}
                    </td>
                  </tr>
                );
              })}
              {grants.length === 0 && (
                <tr>
                  <td colSpan={6} className="py-4 text-center text-ink-3">
                    No break-glass grants yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <GrantBreakGlassForm />
    </div>
  );
}
