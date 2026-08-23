'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { createClient } from '@/lib/supabase/client';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Card, Field, Input, Select, Alert } from '@/components/ui';

type Matter = { id: string; matter_label: string; status: string };

export function GrantMatterAccessForm() {
  const router = useRouter();
  const [matters, setMatters] = useState<Matter[]>([]);
  const [mattersError, setMattersError] = useState<string | null>(null);
  const [matterId, setMatterId] = useState('');
  const [userId, setUserId] = useState('');
  const [reason, setReason] = useState('');
  const [expiresAt, setExpiresAt] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // admin-list-matters-for-grant is a GET-only Edge Function. The shared
    // callEdgeFunction helper always POSTs via functions.invoke(), so this
    // one call bypasses it and invokes with method: 'GET' directly.
    async function loadMatters() {
      const supabase = createClient();
      const { data, error: invokeError } = await supabase.functions.invoke<{ matters: Matter[] }>(
        'admin-list-matters-for-grant',
        { method: 'GET' },
      );
      if (invokeError) {
        setMattersError(invokeError.message);
        return;
      }
      setMatters(data?.matters ?? []);
      if (data?.matters?.[0]) setMatterId(data.matters[0].id);
    }
    loadMatters();
  }, []);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setLoading(true);
    try {
      const result = await callEdgeFunction<{ matter_access_id: string }>('admin-grant-matter-access', {
        matter_id: matterId,
        user_id: userId,
        reason,
        expires_at: expiresAt ? new Date(expiresAt).toISOString() : undefined,
      });
      setSuccess(`Granted. matter_access_id=${result.matter_access_id}`);
      setUserId('');
      setReason('');
      setExpiresAt('');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Grant failed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Grant matter access">
      <form onSubmit={onSubmit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        {mattersError && <Alert>{mattersError}</Alert>}
        {success && <Alert tone="seal">{success}</Alert>}
        <Field label="Matter">
          <Select required value={matterId} onChange={(e) => setMatterId(e.target.value)}>
            {matters.length === 0 && <option value="">No matters available</option>}
            {matters.map((m) => (
              <option key={m.id} value={m.id}>
                {m.matter_label} ({m.status})
              </option>
            ))}
          </Select>
        </Field>
        <Field label="User ID">
          <Input required value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="user UUID" />
        </Field>
        <Field label="Reason">
          <Input required value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <Field label="Expires at" hint="Optional.">
          <Input type="datetime-local" value={expiresAt} onChange={(e) => setExpiresAt(e.target.value)} />
        </Field>
        <p className="text-xs text-ink-3">
          A lawyer may only grant access on a matter they already hold; an
          owner/admin may grant on any matter, but never to themselves —
          both enforced server-side.
        </p>
        <Button type="submit" disabled={loading || !matterId}>
          {loading ? 'Granting…' : 'Grant access'}
        </Button>
      </form>
    </Card>
  );
}
