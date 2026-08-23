'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Card, Field, Input, Alert } from '@/components/ui';

type PriorAccess = { storage_path: string; action: string; accessed_at: string };
type CreateWallResult = {
  wall_id: string;
  prior_access_count: number;
  prior_access: PriorAccess[];
};

export function CreateWallForm() {
  const router = useRouter();
  const [matterId, setMatterId] = useState('');
  const [userId, setUserId] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CreateWallResult | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setResult(null);
    setLoading(true);
    try {
      const created = await callEdgeFunction<CreateWallResult>('admin-manage-ethical-wall', {
        action: 'create',
        matter_id: matterId,
        user_id: userId,
        reason,
      });
      setResult(created);
      setMatterId('');
      setUserId('');
      setReason('');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create wall failed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Create wall">
      <form onSubmit={onSubmit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        {result && (
          <Alert tone="warn">
            <p className="font-medium">
              Wall {result.wall_id} created. {result.prior_access_count} prior access{' '}
              {result.prior_access_count === 1 ? 'event' : 'events'} found before the wall went up.
            </p>
            {result.prior_access.length > 0 && (
              <ul className="mt-2 space-y-1 font-mono text-xs">
                {result.prior_access.map((a, i) => (
                  <li key={i}>
                    {new Date(a.accessed_at).toLocaleString()} — {a.action} — {a.storage_path}
                  </li>
                ))}
              </ul>
            )}
          </Alert>
        )}
        <Field label="Matter ID">
          <Input required value={matterId} onChange={(e) => setMatterId(e.target.value)} placeholder="matter UUID" />
        </Field>
        <Field label="User ID">
          <Input required value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="user UUID" />
        </Field>
        <Field label="Reason">
          <Input required value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <Button type="submit" disabled={loading}>
          {loading ? 'Creating…' : 'Create wall'}
        </Button>
      </form>
    </Card>
  );
}
