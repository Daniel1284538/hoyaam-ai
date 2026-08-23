import { createClient } from '@/lib/supabase/server';
import { Card, Badge } from '@/components/ui';
import { EnableGuardrailButton } from './enable-guardrail-button';
import { RequestDisableForm } from './request-disable-form';
import { ChangeRequestActions } from './change-request-actions';

type GuardrailRow = {
  id: string;
  guardrail_key: string;
  label_en: string;
  label_ar: string;
  description: string | null;
  enabled: boolean;
  updated_by: string | null;
  updated_at: string | null;
};

type ChangeRequestRow = {
  id: string;
  guardrail_id: string;
  requested_change: 'disable' | 'enable';
  reason: string | null;
  requested_by: string | null;
  requested_at: string | null;
  status: 'pending' | 'approved' | 'rejected';
  approved_by: string | null;
  approved_at: string | null;
};

export default async function GuardrailsPage() {
  const supabase = await createClient();
  const [{ data: guardrailData, error: guardrailError }, { data: requestData, error: requestError }] =
    await Promise.all([
      supabase.from('guardrails').select('*').order('guardrail_key', { ascending: true }),
      supabase.from('guardrail_change_requests').select('*').order('requested_at', { ascending: false }),
    ]);

  const guardrails = (guardrailData ?? []) as GuardrailRow[];
  const requests = (requestData ?? []) as ChangeRequestRow[];
  const guardrailByKey = new Map(guardrails.map((g) => [g.id, g]));

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Guardrails</h1>
        <p className="mt-1 text-sm text-ink-2">
          Enabling a guardrail (tightening) applies immediately. Disabling requires a change request
          approved by someone other than the requester.
        </p>
      </div>

      <Card title="Guardrails">
        {guardrailError && (
          <p className="text-sm text-risk">Failed to load guardrails: {guardrailError.message}</p>
        )}
        {!guardrailError && guardrails.length === 0 && (
          <p className="text-sm text-ink-3">No guardrails found.</p>
        )}
        <div className="space-y-4">
          {guardrails.map((g) => (
            <div key={g.id} className="rounded-md border border-rule p-4">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm text-ink">{g.guardrail_key}</span>
                    <Badge tone={g.enabled ? 'seal' : 'risk'}>{g.enabled ? 'enabled' : 'disabled'}</Badge>
                  </div>
                  <p className="mt-1 text-sm text-ink">{g.label_en}</p>
                  <p className="text-sm text-ink-2" dir="rtl">
                    {g.label_ar}
                  </p>
                  {g.description && <p className="mt-1 text-xs text-ink-3">{g.description}</p>}
                  <p className="mt-1 text-xs text-ink-3">
                    updated {g.updated_at ?? '—'} by {g.updated_by ?? '—'}
                  </p>
                </div>
                {!g.enabled && <EnableGuardrailButton guardrailKey={g.guardrail_key} />}
              </div>
            </div>
          ))}
        </div>
      </Card>

      <Card title="Request a disable">
        <RequestDisableForm guardrails={guardrails.filter((g) => g.enabled)} />
      </Card>

      <Card title="Change requests">
        {requestError && (
          <p className="text-sm text-risk">Failed to load change requests: {requestError.message}</p>
        )}
        {!requestError && requests.length === 0 && (
          <p className="text-sm text-ink-3">No change requests yet.</p>
        )}
        <div className="space-y-3">
          {requests.map((r) => {
            const guardrail = guardrailByKey.get(r.guardrail_id);
            return (
              <div key={r.id} className="rounded-md border border-rule p-4">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-sm text-ink">
                    {guardrail?.guardrail_key ?? r.guardrail_id}
                  </span>
                  <Badge tone="default">{r.requested_change}</Badge>
                  <Badge
                    tone={r.status === 'approved' ? 'seal' : r.status === 'rejected' ? 'risk' : 'warn'}
                  >
                    {r.status}
                  </Badge>
                </div>
                {r.reason && <p className="mt-1 text-sm text-ink-2">{r.reason}</p>}
                <p className="mt-1 text-xs text-ink-3">
                  requested {r.requested_at ?? '—'} by {r.requested_by ?? '—'}
                  {r.status !== 'pending' &&
                    ` · ${r.status} ${r.approved_at ?? '—'} by ${r.approved_by ?? '—'}`}
                </p>
                {r.status === 'pending' && <ChangeRequestActions requestId={r.id} />}
              </div>
            );
          })}
        </div>
      </Card>
    </div>
  );
}
