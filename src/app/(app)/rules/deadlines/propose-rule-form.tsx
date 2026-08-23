'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Field, Input, Select, Textarea, Alert } from '@/components/ui';

type ProposeResponse = { rule_id: string; status: string };

const initialForm = {
  rule_key: '',
  title_ar: '',
  title_en: '',
  trigger_event: '',
  duration_value: '',
  duration_unit: 'calendar_days',
  citation: '',
};

export function ProposeRuleForm() {
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
      await callEdgeFunction<ProposeResponse>('admin-propose-deadline-rule', {
        rule_key: form.rule_key,
        title_ar: form.title_ar,
        title_en: form.title_en,
        trigger_event: form.trigger_event,
        duration_value: Number(form.duration_value),
        duration_unit: form.duration_unit,
        citation: form.citation,
      });
      setForm(initialForm);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to propose rule.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {error && <Alert>{error}</Alert>}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Rule key" hint="e.g. cassation_appeal_window">
          <Input required value={form.rule_key} onChange={(e) => update('rule_key', e.target.value)} />
        </Field>
        <Field label="Trigger event">
          <Input required value={form.trigger_event} onChange={(e) => update('trigger_event', e.target.value)} />
        </Field>
        <Field label="Title (Arabic)">
          <Input required dir="rtl" value={form.title_ar} onChange={(e) => update('title_ar', e.target.value)} />
        </Field>
        <Field label="Title (English)">
          <Input required value={form.title_en} onChange={(e) => update('title_en', e.target.value)} />
        </Field>
        <Field label="Duration value">
          <Input
            type="number"
            required
            min={1}
            value={form.duration_value}
            onChange={(e) => update('duration_value', e.target.value)}
          />
        </Field>
        <Field label="Duration unit">
          <Select required value={form.duration_unit} onChange={(e) => update('duration_unit', e.target.value)}>
            <option value="calendar_days">Calendar days</option>
            <option value="working_days">Working days</option>
            <option value="months">Months</option>
          </Select>
        </Field>
      </div>
      <Field label="Citation" hint="mandatory — a rule can't exist in this system without a citation">
        <Textarea required rows={2} value={form.citation} onChange={(e) => update('citation', e.target.value)} />
      </Field>
      <Button type="submit" disabled={loading}>
        {loading ? 'Submitting…' : 'Propose rule'}
      </Button>
    </form>
  );
}
