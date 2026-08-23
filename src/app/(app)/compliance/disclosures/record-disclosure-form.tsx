'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Field, Input, Textarea, Alert } from '@/components/ui';

type RecordResponse = { disclosure_id: string };

const initialForm = {
  matter_id: '',
  client_name: '',
  client_identifier: '',
  addendum_version: '',
  signed_at: '',
  notes: '',
};

export function RecordDisclosureForm() {
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
      await callEdgeFunction<RecordResponse>('admin-manage-disclosures', {
        matter_id: form.matter_id || null,
        client_name: form.client_name,
        client_identifier: form.client_identifier,
        addendum_version: form.addendum_version,
        signed_at: form.signed_at ? new Date(form.signed_at).toISOString() : undefined,
        notes: form.notes || undefined,
      });
      setForm(initialForm);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to record disclosure.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Client name">
          <Input required value={form.client_name} onChange={(e) => update('client_name', e.target.value)} />
        </Field>
        <Field label="Client identifier" hint="national ID / passport / CR number">
          <Input
            required
            value={form.client_identifier}
            onChange={(e) => update('client_identifier', e.target.value)}
          />
        </Field>
        <Field label="Addendum version">
          <Input
            required
            value={form.addendum_version}
            onChange={(e) => update('addendum_version', e.target.value)}
          />
        </Field>
        <Field label="Matter" hint="optional — matter UUID">
          <Input placeholder="matter UUID" value={form.matter_id} onChange={(e) => update('matter_id', e.target.value)} />
        </Field>
        <Field label="Signed at" hint="optional — defaults to now">
          <Input type="datetime-local" value={form.signed_at} onChange={(e) => update('signed_at', e.target.value)} />
        </Field>
      </div>
      <Field label="Notes" hint="optional">
        <Textarea rows={2} value={form.notes} onChange={(e) => update('notes', e.target.value)} />
      </Field>
      <Button type="submit" disabled={loading}>
        {loading ? 'Recording…' : 'Record disclosure'}
      </Button>
    </form>
  );
}
