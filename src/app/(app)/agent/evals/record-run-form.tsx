'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Field, Select, Input, Textarea, Button, Alert } from '@/components/ui';

type EvalSuiteRow = { id: string; suite_key: string };

type RecordRunResult = {
  run_id: string;
  suite_key: string;
  score: number;
  passed: boolean;
  gates_publish: boolean;
};

export function RecordRunForm({ suites }: { suites: EvalSuiteRow[] }) {
  const router = useRouter();
  const [suiteKey, setSuiteKey] = useState('');
  const [score, setScore] = useState('');
  const [details, setDetails] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<RecordRunResult | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setResult(null);
    if (!suiteKey) {
      setError('Choose a suite.');
      return;
    }
    setSubmitting(true);
    try {
      const data = await callEdgeFunction<RecordRunResult>('admin-record-eval-run', {
        suite_key: suiteKey,
        score: Number(score),
        details: details || undefined,
      });
      setResult(data);
      setScore('');
      setDetails('');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to record run.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {error && <Alert tone="risk">{error}</Alert>}
      {result && (
        <Alert tone={result.passed ? 'seal' : 'risk'}>
          Run recorded for {result.suite_key}: score {result.score} — {result.passed ? 'passed' : 'failed'}
          {result.gates_publish ? ' (gates publish)' : ' (informational)'}
        </Alert>
      )}
      <Field label="Suite">
        <Select value={suiteKey} onChange={(e) => setSuiteKey(e.target.value)} required>
          <option value="">Select a suite…</option>
          {suites.map((s) => (
            <option key={s.id} value={s.suite_key}>
              {s.suite_key}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Score" hint="0–1">
        <Input
          type="number"
          min={0}
          max={1}
          step={0.01}
          value={score}
          onChange={(e) => setScore(e.target.value)}
          required
        />
      </Field>
      <Field label="Details" hint="Optional free text or JSON.">
        <Textarea value={details} onChange={(e) => setDetails(e.target.value)} rows={4} />
      </Field>
      <Button type="submit" disabled={submitting}>
        {submitting ? 'Recording…' : 'Record run'}
      </Button>
    </form>
  );
}
