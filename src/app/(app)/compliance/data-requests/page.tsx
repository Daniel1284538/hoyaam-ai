import { createClient } from '@/lib/supabase/server';
import { getViewer } from '@/lib/capabilities';
import { Card, Badge } from '@/components/ui';
import { CreateRequestForm } from './create-request-form';
import { RequestRowActions } from './request-row-actions';

export type DataRequestRow = {
  id: string;
  request_type: 'locate' | 'export' | 'erasure';
  subject_name: string;
  subject_identifier: string;
  matter_id: string | null;
  received_via: string | null;
  status: 'received' | 'scoped' | 'decided' | 'fulfilled';
  // Matches the actual shape admin-data-request's 'scope' action returns:
  // arrays of full row objects (disclosures / matters / holds matched by
  // best-effort text search), not counts or id strings.
  scope_report: {
    disclosures_found?: { id: string; matter_id: string | null; client_name: string; client_identifier: string; addendum_version: string; signed_at: string }[];
    matters_referenced?: { id: string; matter_label: string; status: string }[];
    matters_under_active_hold?: { id: string; matter_id: string; reason: string; applied_at: string }[];
    note?: string;
  } | null;
  scoped_by: string | null;
  scoped_at: string | null;
  decision: 'erase_approved' | 'erase_refused' | 'export_approved' | 'no_action' | null;
  decision_reason: string | null;
  decided_by: string | null;
  decided_at: string | null;
  fulfilled_at: string | null;
  created_by: string | null;
  created_at: string;
};

const STATUS_TONE = {
  received: 'warn',
  scoped: 'warn',
  decided: 'warn',
  fulfilled: 'seal',
} as const;

export default async function DataRequestsPage() {
  const [viewer, supabase] = await Promise.all([getViewer(), createClient()]);
  const canHandle = viewer?.capabilities.has('handle_data_request') ?? false;

  const { data, error } = await supabase
    .from('data_requests')
    .select('*')
    .order('created_at', { ascending: false });

  const requests = (data ?? []) as DataRequestRow[];

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Data subject requests</h1>
        <p className="mt-1 text-sm text-ink-2">
          erase_approved here only records the decision — it doesn&apos;t delete anything. Actual
          erasure happens on the Purge page, linking back to this request.
        </p>
      </div>

      {error && <p className="text-sm text-risk">Failed to load data requests: {error.message}</p>}

      {canHandle && (
        <Card title="Create request">
          <CreateRequestForm />
        </Card>
      )}

      <Card title="Requests">
        {!error && requests.length === 0 && <p className="text-sm text-ink-3">No data subject requests yet.</p>}
        <ul className="space-y-3">
          {requests.map((req) => (
            <li key={req.id} className="rounded-md border border-rule p-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={STATUS_TONE[req.status]}>{req.status}</Badge>
                  <span className="font-mono text-xs text-ink">{req.request_type}</span>
                  <span className="font-medium text-ink">{req.subject_name}</span>
                  <span className="font-mono text-xs text-ink-3">{req.subject_identifier}</span>
                </div>
                <span className="text-xs text-ink-3">{new Date(req.created_at).toLocaleString()}</span>
              </div>
              <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-ink-2 sm:grid-cols-3">
                <div>
                  <dt className="text-ink-3">Matter</dt>
                  <dd className="font-mono text-ink">{req.matter_id ?? '—'}</dd>
                </div>
                <div>
                  <dt className="text-ink-3">Received via</dt>
                  <dd className="text-ink">{req.received_via ?? '—'}</dd>
                </div>
                {req.decision && (
                  <div>
                    <dt className="text-ink-3">Decision</dt>
                    <dd className="text-ink">{req.decision}</dd>
                  </div>
                )}
              </dl>
              {req.decision_reason && (
                <p className="mt-2 text-ink-2">
                  <span className="text-ink-3">reason: </span>
                  {req.decision_reason}
                </p>
              )}
              {req.scope_report && (
                <div className="mt-2 rounded-md bg-surface-2 p-2 text-xs text-ink-2">
                  <p>
                    disclosures found:{' '}
                    {(req.scope_report.disclosures_found ?? []).map((d) => d.client_name).join(', ') || 'none'}
                  </p>
                  <p>
                    matters referenced:{' '}
                    {(req.scope_report.matters_referenced ?? []).map((m) => m.matter_label).join(', ') || 'none'}
                  </p>
                  <p>
                    matters under active hold:{' '}
                    {(req.scope_report.matters_under_active_hold ?? []).map((h) => h.matter_id).join(', ') || 'none'}
                  </p>
                  {req.scope_report.note && <p className="mt-1 italic text-ink-3">{req.scope_report.note}</p>}
                </div>
              )}
              {canHandle && (req.status === 'received' || req.status === 'scoped' || req.status === 'decided') && (
                <div className="mt-3 border-t border-rule pt-3">
                  <RequestRowActions requestId={req.id} status={req.status} />
                </div>
              )}
            </li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
