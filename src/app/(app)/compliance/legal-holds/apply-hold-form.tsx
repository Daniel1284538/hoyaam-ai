'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Field, Input, Textarea, Alert } from '@/components/ui';

type ApplyResponse = { hold_id: string };

const initialForm = { matter_id: '', reason: '' };

export function ApplyHoldForm() {
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
      await callEdgeFunction<ApplyResponse>('admin-apply-legal-hold', {
        action: 'apply',
        matter_id: form.matter_id,
        reason: form.reason,
      });
      setForm(initialForm);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to apply hold.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {error && <Alert>{error}</Alert>}
      <Field label="Matter" hint="matter UUID">
        <Input required placeholder="matter UUID" value={form.matter_id} onChange={(e) => update('matter_id', e.target.value)} />
      </Field>
      <Field label="Reason">
        <Textarea required rows={2} value={form.reason} onChange={(e) => update('reason', e.target.value)} />
      </Field>
      <Button type="submit" disabled={loading}>
        {loading ? 'Applying…' : 'Apply hold'}
      </Button>
    </form>
  );
}
