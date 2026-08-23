import { createClient } from '@/lib/supabase/server';
import { Card, Badge } from '@/components/ui';

type AuditLogRow = {
  id: string;
  actor_id: string | null;
  action: string;
  target_table: string | null;
  target_id: string | null;
  before: unknown;
  after: unknown;
  reason: string | null;
  success: boolean;
  created_at: string;
};

type DocumentAccessLogRow = {
  id: string;
  matter_id: string | null;
  bucket_id: string | null;
  storage_path: string | null;
  user_id: string | null;
  action: 'view' | 'download' | 'denied';
  reason: string | null;
  accessed_at: string;
};

export default async function AuditLogPage() {
  const supabase = await createClient();

  const [{ data: auditData, error: auditError }, { data: docData, error: docError }] = await Promise.all([
    supabase.from('audit_log').select('*').order('created_at', { ascending: false }).limit(100),
    supabase.from('document_access_log').select('*').order('accessed_at', { ascending: false }).limit(100),
  ]);

  const auditRows = (auditData ?? []) as AuditLogRow[];
  const docRows = (docData ?? []) as DocumentAccessLogRow[];

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Audit log</h1>
        <p className="mt-1 text-sm text-ink-2">
          Every privileged action taken through this portal&apos;s Edge Functions, including denied
          attempts. Read-only.
        </p>
      </div>

      <Card title="Audit log" action={<span className="text-xs text-ink-3">showing latest 100</span>}>
        {auditError && <p className="text-sm text-risk">Failed to load audit log: {auditError.message}</p>}
        {!auditError && auditRows.length === 0 && <p className="text-sm text-ink-3">No audit entries yet.</p>}
        <ul className="space-y-3">
          {auditRows.map((row) => (
            <li key={row.id} className="rounded-md border border-rule p-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  {!row.success && <Badge tone="risk">denied</Badge>}
                  <span className="font-mono text-xs text-ink">{row.action}</span>
                  {row.target_table && (
                    <span className="text-xs text-ink-3">
                      on {row.target_table}
                      {row.target_id ? ` (${row.target_id})` : ''}
                    </span>
                  )}
                </div>
                <span className="text-xs text-ink-3">{new Date(row.created_at).toLocaleString()}</span>
              </div>
              <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-ink-2 sm:grid-cols-3">
                <div>
                  <dt className="text-ink-3">Actor</dt>
                  <dd className="font-mono text-ink">{row.actor_id ?? '—'}</dd>
                </div>
                {row.reason && (
                  <div className="col-span-2">
                    <dt className="text-ink-3">Reason</dt>
                    <dd className="text-ink">{row.reason}</dd>
                  </div>
                )}
              </dl>
              {row.before != null && (
                <details className="mt-2">
                  <summary className="cursor-pointer text-xs text-ink-3">before</summary>
                  <pre className="mt-1 max-h-64 overflow-auto rounded-md bg-surface-2 p-3 font-mono text-xs text-ink-2">
                    {JSON.stringify(row.before, null, 2)}
                  </pre>
                </details>
              )}
              {row.after != null && (
                <details className="mt-2">
                  <summary className="cursor-pointer text-xs text-ink-3">after</summary>
                  <pre className="mt-1 max-h-64 overflow-auto rounded-md bg-surface-2 p-3 font-mono text-xs text-ink-2">
                    {JSON.stringify(row.after, null, 2)}
                  </pre>
                </details>
              )}
            </li>
          ))}
        </ul>
      </Card>

      <Card title="Document access log" action={<span className="text-xs text-ink-3">showing latest 100</span>}>
        {docError && <p className="text-sm text-risk">Failed to load document access log: {docError.message}</p>}
        {!docError && docRows.length === 0 && <p className="text-sm text-ink-3">No document access entries yet.</p>}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-rule text-xs uppercase tracking-wide text-ink-3">
                <th className="py-2 pr-4 font-medium">Action</th>
                <th className="py-2 pr-4 font-medium">Matter</th>
                <th className="py-2 pr-4 font-medium">Path</th>
                <th className="py-2 pr-4 font-medium">User</th>
                <th className="py-2 pr-4 font-medium">Reason</th>
                <th className="py-2 pr-4 font-medium">When</th>
              </tr>
            </thead>
            <tbody>
              {docRows.map((row) => (
                <tr key={row.id} className="border-b border-rule last:border-0">
                  <td className="py-2 pr-4">
                    {row.action === 'denied' ? (
                      <Badge tone="risk">denied</Badge>
                    ) : (
                      <Badge tone="default">{row.action}</Badge>
                    )}
                  </td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-2">{row.matter_id ?? '—'}</td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-2">
                    {row.bucket_id ? `${row.bucket_id}/${row.storage_path ?? ''}` : row.storage_path ?? '—'}
                  </td>
                  <td className="py-2 pr-4 font-mono text-xs text-ink-2">{row.user_id ?? '—'}</td>
                  <td className="py-2 pr-4 text-xs text-ink-2">{row.reason ?? '—'}</td>
                  <td className="py-2 pr-4 text-xs text-ink-3">{new Date(row.accessed_at).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
