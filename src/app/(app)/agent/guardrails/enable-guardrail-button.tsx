'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Alert } from '@/components/ui';

export function EnableGuardrailButton({ guardrailKey }: { guardrailKey: string }) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onClick() {
    setError(null);
    setSubmitting(true);
    try {
      await callEdgeFunction('admin-guardrail-change', { action: 'enable', guardrail_key: guardrailKey });
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to enable guardrail.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <Button type="button" onClick={onClick} disabled={submitting}>
        {submitting ? 'Enabling…' : 'Enable'}
      </Button>
      {error && <Alert tone="risk">{error}</Alert>}
    </div>
  );
}
