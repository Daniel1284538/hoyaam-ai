'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Field, Select, Textarea, Button, Alert } from '@/components/ui';

type GuardrailRow = { id: string; guardrail_key: string; label_en: string };

export function RequestDisableForm({ guardrails }: { guardrails: GuardrailRow[] }) {
  const router = useRouter();
  const [guardrailKey, setGuardrailKey] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!guardrailKey) {
      setError('Choose a guardrail.');
      return;
    }
    setSubmitting(true);
    try {
      await callEdgeFunction<{ request_id: string; status: string }>('admin-guardrail-change', {
        action: 'request-disable',
        guardrail_key: guardrailKey,
        reason,
      });
      setGuardrailKey('');
      setReason('');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to submit request.');
    } finally {
      setSubmitting(false);
    }
  }

  if (guardrails.length === 0) {
    return <p className="text-sm text-ink-3">No enabled guardrails available to request a disable for.</p>;
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {error && <Alert tone="risk">{error}</Alert>}
      <Field label="Guardrail">
        <Select value={guardrailKey} onChange={(e) => setGuardrailKey(e.target.value)} required>
          <option value="">Select a guardrail…</option>
          {guardrails.map((g) => (
            <option key={g.id} value={g.guardrail_key}>
              {g.guardrail_key} — {g.label_en}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Reason">
        <Textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          required
          placeholder="Why this guardrail should be disabled."
        />
      </Field>
      <Button type="submit" disabled={submitting}>
        {submitting ? 'Submitting…' : 'Request disable'}
      </Button>
    </form>
  );
}
