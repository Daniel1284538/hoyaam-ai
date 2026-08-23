'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Field, Input, Select, Button, Alert } from '@/components/ui';

type RoleRow = { id: string; label_en: string };

export function SetFlagForm({ roles }: { roles: RoleRow[] }) {
  const router = useRouter();
  const [flagKey, setFlagKey] = useState('');
  const [roleId, setRoleId] = useState('');
  const [enabled, setEnabled] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await callEdgeFunction<{ flag_id: string; flag_key: string; role_id: string | null; enabled: boolean }>(
        'admin-set-feature-flag',
        { flag_key: flagKey, role_id: roleId || null, enabled },
      );
      setFlagKey('');
      setRoleId('');
      setEnabled(false);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to set flag.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {error && <Alert tone="risk">{error}</Alert>}
      <Field label="Flag key">
        <Input
          value={flagKey}
          onChange={(e) => setFlagKey(e.target.value)}
          placeholder="e.g. draft_export_enabled"
          required
        />
      </Field>
      <Field label="Role" hint="Leave blank to set the global default.">
        <Select value={roleId} onChange={(e) => setRoleId(e.target.value)}>
          <option value="">— global default —</option>
          {roles.map((r) => (
            <option key={r.id} value={r.id}>
              {r.label_en}
            </option>
          ))}
        </Select>
      </Field>
      <label className="flex items-center gap-2 text-sm text-ink-2">
        <input
          type="checkbox"
          checked={enabled}
          onChange={(e) => setEnabled(e.target.checked)}
          className="h-4 w-4 rounded border-rule"
        />
        Enabled
      </label>
      <Button type="submit" disabled={submitting}>
        {submitting ? 'Saving…' : 'Set flag'}
      </Button>
    </form>
  );
}
