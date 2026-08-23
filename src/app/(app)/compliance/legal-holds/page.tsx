import { createClient } from '@/lib/supabase/server';
import { getViewer } from '@/lib/capabilities';
import { Card, Badge } from '@/components/ui';
import { ApplyHoldForm } from './apply-hold-form';
import { LiftHoldButton } from './lift-hold-button';

export type LegalHoldRow = {
  id: string;
  matter_id: string;
  reason: string;
  applied_by: string | null;
  applied_at: string;
  lifted_at: string | null;
  lifted_by: string | null;
};

export default async function LegalHoldsPage() {
  const [viewer, supabase] = await Promise.all([getViewer(), createClient()]);
  const canApply = viewer?.capabilities.has('apply_legal_hold') ?? false;

  const { data, error } = await supabase
    .from('legal_holds')
    .select('*')
    .order('applied_at', { ascending: false });

  const holds = (data ?? []) as LegalHoldRow[];

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Legal holds</h1>
        <p className="mt-1 text-sm text-ink-2">
          While a hold is active, the matter cannot be deleted — enforced by a database trigger,
          not just this UI.
        </p>
      </div>

      {error && <p className="text-sm text-risk">Failed to load legal holds: {error.message}</p>}

      {canApply && (
        <Card title="Apply hold">
          <ApplyHoldForm />
        </Card>
      )}

      <Card title="Holds">
        {!error && holds.length === 0 && <p className="text-sm text-ink-3">No legal holds yet.</p>}
        <ul className="space-y-3">
          {holds.map((hold) => (
            <li key={hold.id} className="rounded-md border border-rule p-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  {hold.lifted_at == null ? (
                    <Badge tone="risk">Active hold</Badge>
                  ) : (
                    <Badge tone="default">Lifted</Badge>
                  )}
                  <span className="font-mono text-xs text-ink">{hold.matter_id}</span>
                </div>
                <span className="text-xs text-ink-3">
                  applied {new Date(hold.applied_at).toLocaleString()}
                </span>
              </div>
              <p className="mt-2 text-ink-2">{hold.reason}</p>
              {hold.lifted_at != null && (
                <p className="mt-1 text-xs text-ink-3">
                  lifted {new Date(hold.lifted_at).toLocaleString()} by {hold.lifted_by ?? '—'}
                </p>
              )}
              {hold.lifted_at == null && canApply && (
                <div className="mt-3 border-t border-rule pt-3">
                  <LiftHoldButton holdId={hold.id} />
                </div>
              )}
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
