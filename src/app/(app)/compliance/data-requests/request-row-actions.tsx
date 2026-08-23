'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Field, Select, Textarea, Alert } from '@/components/ui';
import type { DataRequestRow } from './page';

// Matches the actual shape admin-data-request's 'scope' action returns:
// arrays of full row objects, not counts or id strings.
type ScopeResponse = {
  request_id: string;
  status: string;
  report: {
    disclosures_found: { id: string; matter_id: string | null; client_name: string; client_identifier: string; addendum_version: string; signed_at: string }[];
    matters_referenced: { id: string; matter_label: string; status: string }[];
    matters_under_active_hold: { id: string; matter_id: string; reason: string; applied_at: string }[];
    note: string;
  };
};

type DecideResponse = { request_id: string; status: string; decision: string };

const DECISIONS: DataRequestRow['decision'][] = [
  'erase_approved',
  'erase_refused',
  'export_approved',
  'no_action',
];

export function RequestRowActions({
  requestId,
  status,
}: {
  requestId: string;
  status: DataRequestRow['status'];
}) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [scopeReport, setScopeReport] = useState<ScopeResponse['report'] | null>(null);
  const [decision, setDecision] = useState<string>('no_action');
  const [decisionReason, setDecisionReason] = useState('');

  async function onScope() {
    setError(null);
    setLoading(true);
    try {
      const res = await callEdgeFunction<ScopeResponse>('admin-data-request', {
        action: 'scope',
        request_id: requestId,
      });
      setScopeReport(res.report);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to scope request.');
    } finally {
      setLoading(false);
    }
  }

  async function onDecide(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await callEdgeFunction<DecideResponse>('admin-data-request', {
        action: 'decide',
        request_id: requestId,
        decision,
        decision_reason: decisionReason,
      });
      setDecisionReason('');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to record decision.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-3">
      {error && <Alert>{error}</Alert>}

      {status === 'received' && (
        <Button type="button" variant="secondary" disabled={loading} onClick={onScope}>
          {loading ? 'Scoping…' : 'Scope'}
        </Button>
      )}

      {scopeReport && (
        <Alert tone="warn">
          <p>
            disclosures found: {scopeReport.disclosures_found.map((d) => d.client_name).join(', ') || 'none'}
          </p>
          <p>
            matters referenced: {scopeReport.matters_referenced.map((m) => m.matter_label).join(', ') || 'none'}
          </p>
          <p>
            matters under active hold:{' '}
            {scopeReport.matters_under_active_hold.map((h) => h.matter_id).join(', ') || 'none'}
          </p>
          <p className="mt-1 italic">{scopeReport.note}</p>
        </Alert>
      )}

      {(status === 'scoped' || status === 'decided') && (
        <form onSubmit={onDecide} className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Decision">
              <Select required value={decision} onChange={(e) => setDecision(e.target.value)}>
                {DECISIONS.map((d) => (
                  <option key={d} value={d ?? ''}>
                    {d}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Field label="Decision reason">
            <Textarea
              required
              rows={2}
              value={decisionReason}
              onChange={(e) => setDecisionReason(e.target.value)}
            />
          </Field>
          <Button type="submit" disabled={loading}>
            {loading ? 'Recording…' : 'Decide'}
          </Button>
        </form>
      )}
    </div>
  );
}
