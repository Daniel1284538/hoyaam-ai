'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Field, Input, Textarea, Alert } from '@/components/ui';

type SetPolicyResponse = { policy_id: string };

const initialForm = { matter_type: '', retention_years: '', basis: '' };

export function SetPolicyForm() {
  const router = useRouter();
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function update<K extends keyof typeof initialForm>(key: K, value: string) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await callEdgeFunction<SetPolicyResponse>('admin-manage-retention-policy', {
        matter_type: form.matter_type,
        retention_years: Number(form.retention_years),
        basis: form.basis,
      });
      setForm(initialForm);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to set retention policy.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Matter type">
          <Input required value={form.matter_type} onChange={(e) => update('matter_type', e.target.value)} />
        </Field>
        <Field label="Retention years">
          <Input
            type="number"
            required
            min={1}
            value={form.retention_years}
            onChange={(e) => update('retention_years', e.target.value)}
          />
        </Field>
      </div>
      <Field
        label="Basis"
        hint="a professional-obligation decision the lawyer makes and records, not a default an engineer picks"
      >
        <Textarea required rows={3} value={form.basis} onChange={(e) => update('basis', e.target.value)} />
      </Field>
      <Button type="submit" disabled={loading}>
        {loading ? 'Saving…' : 'Set policy'}
      </Button>
    </form>
  );
}
