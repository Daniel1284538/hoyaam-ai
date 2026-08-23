'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Field, Input, Select, Alert } from '@/components/ui';

type CreateResponse = { request_id: string; status: 'received' };

const initialForm = {
  request_type: 'locate',
  subject_name: '',
  subject_identifier: '',
  matter_id: '',
  received_via: '',
};

export function CreateRequestForm() {
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
      await callEdgeFunction<CreateResponse>('admin-data-request', {
        action: 'create',
        request_type: form.request_type,
        subject_name: form.subject_name,
        subject_identifier: form.subject_identifier,
        matter_id: form.matter_id || null,
        received_via: form.received_via || undefined,
      });
      setForm(initialForm);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create request.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Request type">
          <Select required value={form.request_type} onChange={(e) => update('request_type', e.target.value)}>
            <option value="locate">Locate</option>
            <option value="export">Export</option>
            <option value="erasure">Erasure</option>
          </Select>
        </Field>
        <Field label="Received via" hint="optional — e.g. email, letter">
          <Input value={form.received_via} onChange={(e) => update('received_via', e.target.value)} />
        </Field>
        <Field label="Subject name">
          <Input required value={form.subject_name} onChange={(e) => update('subject_name', e.target.value)} />
        </Field>
        <Field label="Subject identifier">
          <Input
            required
            value={form.subject_identifier}
            onChange={(e) => update('subject_identifier', e.target.value)}
          />
        </Field>
        <Field label="Matter" hint="optional — matter UUID">
          <Input placeholder="matter UUID" value={form.matter_id} onChange={(e) => update('matter_id', e.target.value)} />
        </Field>
      </div>
      <Button type="submit" disabled={loading}>
        {loading ? 'Creating…' : 'Create request'}
      </Button>
    </form>
  );
}
