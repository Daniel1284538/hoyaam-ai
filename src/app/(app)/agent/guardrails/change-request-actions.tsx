'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Alert, Textarea } from '@/components/ui';

export function ChangeRequestActions({ requestId }: { requestId: string }) {
  const router = useRouter();
  const [rejectReason, setRejectReason] = useState('');
  const [showReject, setShowReject] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function act(action: 'approve' | 'reject', reason?: string) {
    setError(null);
    setSubmitting(true);
    try {
      await callEdgeFunction('admin-guardrail-change', { action, request_id: requestId, reason });
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : `Failed to ${action} request.`);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mt-3 space-y-2">
      {error && <Alert tone="risk">{error}</Alert>}
      <div className="flex flex-wrap gap-2">
        <Button type="button" onClick={() => act('approve')} disabled={submitting}>
          Approve
        </Button>
        <Button
          type="button"
          variant="danger"
          onClick={() => setShowReject((v) => !v)}
          disabled={submitting}
        >
          Reject
        </Button>
      </div>
      {showReject && (
        <div className="space-y-2">
          <Textarea
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            rows={2}
            placeholder="Optional reason for rejection"
          />
          <Button
            type="button"
            variant="danger"
            onClick={() => act('reject', rejectReason || undefined)}
            disabled={submitting}
          >
            {submitting ? 'Submitting…' : 'Confirm reject'}
          </Button>
        </div>
      )}
    </div>
  );
}
