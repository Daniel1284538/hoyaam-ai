'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Card, Field, Select, Alert } from '@/components/ui';

type ActiveWall = { id: string; matter_id: string; user_id: string };

export function LiftWallForm({ activeWalls }: { activeWalls: ActiveWall[] }) {
  const router = useRouter();
  const [wallId, setWallId] = useState(activeWalls[0]?.id ?? '');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setLoading(true);
    try {
      await callEdgeFunction('admin-manage-ethical-wall', { action: 'lift', wall_id: wallId });
      setSuccess(`Lifted wall ${wallId}.`);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Lift wall failed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Lift wall">
      <form onSubmit={onSubmit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        {success && <Alert tone="seal">{success}</Alert>}
        <Field label="Active wall">
          <Select required value={wallId} onChange={(e) => setWallId(e.target.value)} disabled={activeWalls.length === 0}>
            {activeWalls.length === 0 && <option value="">No active walls</option>}
            {activeWalls.map((w) => (
              <option key={w.id} value={w.id}>
                {w.matter_id} / {w.user_id}
              </option>
            ))}
          </Select>
        </Field>
        <Button type="submit" disabled={loading || !wallId} variant="secondary">
          {loading ? 'Lifting…' : 'Lift wall'}
        </Button>
      </form>
    </Card>
  );
}
