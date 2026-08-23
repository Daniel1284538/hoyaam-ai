'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Field, Input, Alert } from '@/components/ui';

type ApproveResponse = { rule_id: string; status: string; effective_from: string };

export function ApproveRuleButton({ ruleId }: { ruleId: string }) {
  const router = useRouter();
  const [effectiveFrom, setEffectiveFrom] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onApprove(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await callEdgeFunction<ApproveResponse>('admin-approve-deadline-rule', {
        rule_id: ruleId,
        ...(effectiveFrom ? { effective_from: effectiveFrom } : {}),
      });
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to approve rule.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={onApprove} className="flex flex-wrap items-end gap-3">
      {error && (
        <div className="w-full">
          <Alert>{error}</Alert>
        </div>
      )}
      <Field label="Effective from" hint="optional — defaults to today">
        <Input type="date" value={effectiveFrom} onChange={(e) => setEffectiveFrom(e.target.value)} />
      </Field>
      <Button type="submit" variant="secondary" disabled={loading}>
        {loading ? 'Approving…' : 'Approve'}
      </Button>
    </form>
  );
}
