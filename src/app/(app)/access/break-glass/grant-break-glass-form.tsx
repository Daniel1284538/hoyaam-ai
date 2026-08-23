'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Card, Field, Input, Alert } from '@/components/ui';

type GrantResult = {
  grant_id: string;
  expires_at: string;
  blocked_by_ethical_wall: boolean;
  owner_alert_sent: boolean;
};

export function GrantBreakGlassForm() {
  const router = useRouter();
  const [matterId, setMatterId] = useState('');
  const [userId, setUserId] = useState('');
  const [reason, setReason] = useState('');
  const [durationHours, setDurationHours] = useState('4');
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<GrantResult | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setResult(null);
    setLoading(true);
    try {
      const granted = await callEdgeFunction<GrantResult>('admin-break-glass-grant', {
        matter_id: matterId,
        user_id: userId,
        reason,
        duration_hours: durationHours ? Number(durationHours) : undefined,
      });
      setResult(granted);
      setMatterId('');
      setUserId('');
      setReason('');
      setDurationHours('4');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Grant failed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Grant break-glass access">
      <form onSubmit={onSubmit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        {result && (
          <div className="space-y-2">
            <Alert tone="seal">
              Grant {result.grant_id} created, expires {new Date(result.expires_at).toLocaleString()}.
            </Alert>
            {result.blocked_by_ethical_wall && (
              <Alert tone="warn">
                An ethical wall covers this user/matter. The grant was still
                created — access is still blocked at read time by
                can_access_matter(), which checks the wall separately.
              </Alert>
            )}
            {!result.owner_alert_sent && (
              <p className="text-xs text-ink-3">
                owner_alert_sent: false — no notification channel is wired
                up yet, per the Edge Function&apos;s own comment. Honest gap,
                not an error.
              </p>
            )}
          </div>
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
        <Field label="Duration (hours)" hint="Default 4, capped at 8 regardless.">
          <Input type="number" min={1} max={8} value={durationHours} onChange={(e) => setDurationHours(e.target.value)} />
        </Field>
        <Button type="submit" disabled={loading} variant="danger">
          {loading ? 'Granting…' : 'Grant break-glass access'}
        </Button>
      </form>
    </Card>
  );
}
