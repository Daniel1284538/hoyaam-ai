'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Field, Input, Alert } from '@/components/ui';

type AddResponse = { id: string };

const initialForm = {
  date_from: '',
  date_to: '',
  label_ar: '',
  label_en: '',
  is_court_vacation: false,
};

export function AddCalendarEntryForm() {
  const router = useRouter();
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function update<K extends keyof typeof initialForm>(key: K, value: (typeof initialForm)[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await callEdgeFunction<AddResponse>('admin-manage-court-calendar', {
        action: 'add',
        date_from: form.date_from,
        date_to: form.date_to || form.date_from,
        label_ar: form.label_ar,
        label_en: form.label_en,
        is_court_vacation: form.is_court_vacation,
      });
      setForm(initialForm);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add calendar entry.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Date from">
          <Input
            type="date"
            required
            value={form.date_from}
            onChange={(e) => update('date_from', e.target.value)}
          />
        </Field>
        <Field label="Date to" hint="leave blank for a single-day holiday">
          <Input type="date" value={form.date_to} onChange={(e) => update('date_to', e.target.value)} />
        </Field>
        <Field label="Label (Arabic)">
          <Input required dir="rtl" value={form.label_ar} onChange={(e) => update('label_ar', e.target.value)} />
        </Field>
        <Field label="Label (English)">
          <Input required value={form.label_en} onChange={(e) => update('label_en', e.target.value)} />
        </Field>
      </div>
      <label className="flex items-center gap-2 text-sm text-ink-2">
        <input
          type="checkbox"
          checked={form.is_court_vacation}
          onChange={(e) => update('is_court_vacation', e.target.checked)}
          className="h-4 w-4 rounded border-rule"
        />
        Court-vacation window
      </label>
      <Button type="submit" disabled={loading}>
        {loading ? 'Adding…' : 'Add entry'}
      </Button>
    </form>
  );
}
