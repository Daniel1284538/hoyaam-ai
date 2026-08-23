'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Field, Input, Textarea, Alert, Badge } from '@/components/ui';

type PreviewResponse = {
  matter_id: string;
  matter_label: string;
  status: string;
  under_legal_hold: boolean;
  blocking_hold: unknown;
  purgeable: boolean;
  scope: string;
};

type PurgeResponse = { matter_id: string; purged: true };

export function PurgeFlow() {
  const router = useRouter();
  const [matterId, setMatterId] = useState('');
  const [preview, setPreview] = useState<PreviewResponse | null>(null);
  const [reason, setReason] = useState('');
  const [dataRequestId, setDataRequestId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [purgedOk, setPurgedOk] = useState(false);
  const [loading, setLoading] = useState(false);

  async function onPreview(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setPurgedOk(false);
    setLoading(true);
    try {
      const res = await callEdgeFunction<PreviewResponse>('admin-execute-purge', {
        action: 'preview',
        matter_id: matterId,
      });
      setPreview(res);
    } catch (err) {
      setPreview(null);
      setError(err instanceof Error ? err.message : 'Failed to preview purge.');
    } finally {
      setLoading(false);
    }
  }

  async function onPurge(e: React.FormEvent) {
    e.preventDefault();
    if (!preview) return;
    setError(null);
    setLoading(true);
    try {
      await callEdgeFunction<PurgeResponse>('admin-execute-purge', {
        action: 'purge',
        matter_id: preview.matter_id,
        reason,
        data_request_id: dataRequestId || undefined,
      });
      setPurgedOk(true);
      setPreview(null);
      setReason('');
      setDataRequestId('');
      setMatterId('');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to purge matter.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-6">
      {error && <Alert>{error}</Alert>}
      {purgedOk && <Alert tone="seal">Matter purged.</Alert>}

      <form onSubmit={onPreview} className="space-y-4">
        <Field label="Matter" hint="matter UUID">
          <Input
            required
            placeholder="matter UUID"
            value={matterId}
            onChange={(e) => {
              setMatterId(e.target.value);
              setPreview(null);
            }}
          />
        </Field>
        <Button type="submit" variant="secondary" disabled={loading}>
          {loading ? 'Previewing…' : 'Preview'}
        </Button>
      </form>

      {preview && (
        <div className="rounded-md border border-rule p-4 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium text-ink">{preview.matter_label}</span>
            <span className="font-mono text-xs text-ink-3">{preview.status}</span>
            {preview.under_legal_hold ? (
              <Badge tone="risk">Under legal hold — cannot purge</Badge>
            ) : (
              <Badge tone="seal">Purgeable</Badge>
            )}
          </div>
          <p className="mt-2 text-xs text-ink-3">{preview.scope}</p>

          {preview.purgeable && (
            <form onSubmit={onPurge} className="mt-4 space-y-4 border-t border-rule pt-4">
              <Field label="Reason">
                <Textarea required rows={2} value={reason} onChange={(e) => setReason(e.target.value)} />
              </Field>
              <Field
                label="Data request ID"
                hint="leave blank for a routine retention purge (matter must be closed/archived); fill in to execute an approved erasure request"
              >
                <Input value={dataRequestId} onChange={(e) => setDataRequestId(e.target.value)} />
              </Field>
              <Button type="submit" variant="danger" disabled={loading}>
                {loading ? 'Purging…' : 'Purge'}
              </Button>
            </form>
          )}
        </div>
      )}
    </div>
  );
}
