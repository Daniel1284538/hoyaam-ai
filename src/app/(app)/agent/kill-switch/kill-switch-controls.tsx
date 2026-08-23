'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Field, Textarea, Button, Alert } from '@/components/ui';

export function KillSwitchControls({ engaged }: { engaged: boolean }) {
  const router = useRouter();
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onEngage(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await callEdgeFunction('admin-kill-switch', { action: 'engage', reason });
      setReason('');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to engage kill switch.');
    } finally {
      setSubmitting(false);
    }
  }

  async function onDisengage() {
    setError(null);
    setSubmitting(true);
    try {
      await callEdgeFunction('admin-kill-switch', { action: 'disengage' });
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to disengage kill switch.');
    } finally {
      setSubmitting(false);
    }
  }

  if (engaged) {
    return (
      <div className="space-y-3">
        {error && <Alert tone="risk">{error}</Alert>}
        <p className="text-sm text-ink-2">The kill switch is currently engaged.</p>
        <Button type="button" onClick={onDisengage} disabled={submitting}>
          {submitting ? 'Disengaging…' : 'Disengage'}
        </Button>
      </div>
    );
  }

  return (
    <form onSubmit={onEngage} className="space-y-4">
      {error && <Alert tone="risk">{error}</Alert>}
      <Field label="Reason" hint="Optional, but strongly encouraged for the audit trail.">
        <Textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          placeholder="Why is this being engaged?"
        />
      </Field>
      <Button type="submit" variant="danger" disabled={submitting}>
        {submitting ? 'Engaging…' : 'Engage kill switch'}
      </Button>
    </form>
  );
}
