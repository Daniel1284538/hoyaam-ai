'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Card, Field, Input, Select, Alert } from '@/components/ui';
import type { Role } from './page';

export function SetRoleForm({ roles }: { roles: Role[] }) {
  const router = useRouter();
  const [userId, setUserId] = useState('');
  const [roleId, setRoleId] = useState(roles[0]?.id ?? '');
  const [action, setAction] = useState<'grant' | 'revoke'>('grant');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setLoading(true);
    try {
      const result = await callEdgeFunction<{ user_id: string; role_id: string; action: string }>('admin-set-role', {
        user_id: userId,
        role_id: roleId,
        action,
      });
      setSuccess(`${result.action === 'grant' ? 'Granted' : 'Revoked'} ${result.role_id} for ${result.user_id}.`);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Set role failed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Set role">
      <form onSubmit={onSubmit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        {success && <Alert tone="seal">{success}</Alert>}
        <Field label="User ID">
          <Input required value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="user UUID" />
        </Field>
        <Field label="Role">
          <Select required value={roleId} onChange={(e) => setRoleId(e.target.value)}>
            {roles.map((r) => (
              <option key={r.id} value={r.id}>
                {r.label_en}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Action">
          <Select value={action} onChange={(e) => setAction(e.target.value as 'grant' | 'revoke')}>
            <option value="grant">Grant</option>
            <option value="revoke">Revoke</option>
          </Select>
        </Field>
        <Button type="submit" disabled={loading} variant={action === 'revoke' ? 'danger' : 'primary'}>
          {loading ? 'Submitting…' : action === 'grant' ? 'Grant role' : 'Revoke role'}
        </Button>
      </form>
    </Card>
  );
}
