import { createClient } from '@/lib/supabase/server';
import { Card, Badge } from '@/components/ui';
import { CreateWallForm } from './create-wall-form';
import { LiftWallForm } from './lift-wall-form';

type EthicalWallRow = {
  id: string;
  matter_id: string;
  user_id: string;
  reason: string;
  created_at: string;
  lifted_at: string | null;
};

export default async function EthicalWallsPage() {
  const supabase = await createClient();
  const { data: rows } = await supabase
    .from('ethical_walls')
    .select('id, matter_id, user_id, reason, created_at, lifted_at')
    .order('created_at', { ascending: false });

  const walls = (rows ?? []) as EthicalWallRow[];
  const activeWalls = walls.filter((w) => !w.lifted_at);

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Ethical walls</h1>
        <p className="mt-1 text-sm text-ink-2">
          Blocks a specific user from a specific matter, independent of any
          matter-access grant they hold.
        </p>
      </div>

      <Card title="Walls">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-rule text-xs uppercase tracking-wide text-ink-3">
                <th className="py-2 pr-4 font-medium">Matter ID</th>
                <th className="py-2 pr-4 font-medium">User ID</th>
                <th className="py-2 pr-4 font-medium">Reason</th>
                <th className="py-2 pr-4 font-medium">Created</th>
                <th className="py-2 pr-4 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {walls.map((w) => (
                <tr key={w.id} className="border-b border-rule last:border-0">
                  <td className="py-2 pr-4 font-mono text-xs text-ink-2">{w.matter_id}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-2">{w.user_id}</td>
                  <td className="py-2 pr-4 text-ink">{w.reason}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-3">{new Date(w.created_at).toLocaleString()}</td>
                  <td className="py-2 pr-4">
                    {w.lifted_at ? <Badge tone="default">lifted</Badge> : <Badge tone="warn">active</Badge>}
                  </td>
                </tr>
              ))}
              {walls.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-4 text-center text-ink-3">
                    No ethical walls yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <CreateWallForm />
      <LiftWallForm activeWalls={activeWalls.map((w) => ({ id: w.id, matter_id: w.matter_id, user_id: w.user_id }))} />
    </div>
  );
}
