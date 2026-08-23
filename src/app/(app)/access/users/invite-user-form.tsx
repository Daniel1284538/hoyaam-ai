'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Card, Field, Input, Select, Alert } from '@/components/ui';
import type { Role } from './page';

export function InviteUserForm({ roles }: { roles: Role[] }) {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [roleId, setRoleId] = useState(roles[0]?.id ?? '');
  const [fullName, setFullName] = useState('');
  const [supervisorId, setSupervisorId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setLoading(true);
    try {
      const result = await callEdgeFunction<{ user_id: string; invitation_id: string }>('admin-invite-user', {
        email,
        role_id: roleId,
        full_name: fullName || undefined,
        supervisor_id: supervisorId || undefined,
      });
      setSuccess(`Invited. user_id=${result.user_id} invitation_id=${result.invitation_id}`);
      setEmail('');
      setFullName('');
      setSupervisorId('');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invite failed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Invite user">
      <form onSubmit={onSubmit} className="space-y-4">
        {error && <Alert>{error}</Alert>}
        {success && <Alert tone="seal">{success}</Alert>}
        <Field label="Email">
          <Input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
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
        <Field label="Full name" hint="Optional.">
          <Input value={fullName} onChange={(e) => setFullName(e.target.value)} />
        </Field>
        <Field label="Supervisor user ID" hint="Only relevant when the role is Trainee Lawyer. Plain profile UUID.">
          <Input value={supervisorId} onChange={(e) => setSupervisorId(e.target.value)} placeholder="supervisor UUID" />
        </Field>
        <Button type="submit" disabled={loading}>
          {loading ? 'Inviting…' : 'Invite'}
        </Button>
      </form>
    </Card>
  );
}
