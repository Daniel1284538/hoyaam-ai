'use client';

import { useState } from 'react';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Card, Field, Input, Textarea, Alert } from '@/components/ui';

export function RevokeSessionsForm() {
  const [userId, setUserId] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setLoading(true);
    try {
      const result = await callEdgeFunction<{ user_id: string; sessions_deleted: number }>('admin-revoke-sessions', {
        user_id: userId,
        reason,
      });
      setSuccess(`Signed out ${result.user_id} — ${result.sessions_deleted} session(s) deleted.`);
      setReason('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Revoke sessions failed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Revoke sessions">
      <form onSubmit={onSubmit} className="space-y-4">
        <p className="text-sm text-ink-2">Force logout everywhere for a given user.</p>
        {error && <Alert>{error}</Alert>}
        {success && <Alert tone="seal">{success}</Alert>}
        <Field label="User ID">
          <Input required value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="user UUID" />
        </Field>
        <Field label="Reason">
          <Textarea required rows={2} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <Button type="submit" disabled={loading} variant="danger">
          {loading ? 'Revoking…' : 'Revoke sessions'}
        </Button>
      </form>
    </Card>
  );
}
