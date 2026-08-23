'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Alert } from '@/components/ui';

type LiftResponse = { hold_id: string; lifted: true };

export function LiftHoldButton({ holdId }: { holdId: string }) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onLift() {
    setError(null);
    setLoading(true);
    try {
      await callEdgeFunction<LiftResponse>('admin-apply-legal-hold', { action: 'lift', hold_id: holdId });
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to lift hold.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-2">
      {error && <Alert>{error}</Alert>}
      <Button type="button" variant="secondary" disabled={loading} onClick={onLift}>
        {loading ? 'Lifting…' : 'Lift hold'}
      </Button>
    </div>
  );
}
