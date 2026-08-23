import { createClient } from '@/lib/supabase/server';
import { Card, Badge, Alert } from '@/components/ui';
import { KillSwitchControls } from './kill-switch-controls';

type KillSwitchRow = {
  id: number;
  engaged: boolean;
  engaged_by: string | null;
  engaged_at: string | null;
  engaged_reason: string | null;
  disengaged_by: string | null;
  disengaged_at: string | null;
};

export default async function KillSwitchPage() {
  const supabase = await createClient();
  const { data, error } = await supabase.from('agent_kill_switch').select('*').eq('id', 1).maybeSingle();

  const row = data as KillSwitchRow | null;

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Kill switch</h1>
        <p className="mt-1 text-sm text-ink-2">Global emergency stop for the agent pipeline.</p>
      </div>

      <Alert tone="warn">
        HONEST GAP — nothing reads this flag yet before making a model call; there&apos;s no agent
        runtime in this environment to check it. This is the control-plane half only.
      </Alert>

      {error && <Alert tone="risk">Failed to load kill switch state: {error.message}</Alert>}

      {row && (
        <Card title="Current state">
          <div
            className={`rounded-md border p-4 ${
              row.engaged ? 'border-risk/30 bg-risk-soft' : 'border-seal/30 bg-seal-soft'
            }`}
          >
            <Badge tone={row.engaged ? 'risk' : 'seal'}>{row.engaged ? 'ENGAGED' : 'not engaged'}</Badge>
            {row.engaged ? (
              <dl className="mt-3 space-y-1 text-sm text-ink-2">
                <div>
                  <dt className="inline text-ink-3">engaged_by: </dt>
                  <dd className="inline font-mono">{row.engaged_by ?? '—'}</dd>
                </div>
                <div>
                  <dt className="inline text-ink-3">engaged_at: </dt>
                  <dd className="inline font-mono">{row.engaged_at ?? '—'}</dd>
                </div>
                <div>
                  <dt className="inline text-ink-3">reason: </dt>
                  <dd className="inline">{row.engaged_reason ?? '—'}</dd>
                </div>
              </dl>
            ) : (
              <dl className="mt-3 space-y-1 text-sm text-ink-2">
                <div>
                  <dt className="inline text-ink-3">last disengaged_by: </dt>
                  <dd className="inline font-mono">{row.disengaged_by ?? '—'}</dd>
                </div>
                <div>
                  <dt className="inline text-ink-3">last disengaged_at: </dt>
                  <dd className="inline font-mono">{row.disengaged_at ?? '—'}</dd>
                </div>
              </dl>
            )}
          </div>
        </Card>
      )}

      <Card title="Controls">
        <KillSwitchControls engaged={row?.engaged ?? false} />
      </Card>
    </div>
  );
}
