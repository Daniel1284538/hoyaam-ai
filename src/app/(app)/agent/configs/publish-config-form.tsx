'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Field, Input, Textarea, Button, Alert } from '@/components/ui';

export function PublishConfigForm() {
  const router = useRouter();
  const [versionLabel, setVersionLabel] = useState('');
  const [configText, setConfigText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    let parsed: unknown;
    try {
      parsed = JSON.parse(configText);
    } catch {
      setError('Config is not valid JSON — check for trailing commas or unquoted keys.');
      return;
    }

    setSubmitting(true);
    try {
      await callEdgeFunction<{ config_id: string; status: string }>('admin-publish-agent-config', {
        config: parsed,
        version_label: versionLabel,
      });
      setVersionLabel('');
      setConfigText('');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to publish config.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {error && <Alert tone="risk">{error}</Alert>}
      <Field label="Version label">
        <Input
          value={versionLabel}
          onChange={(e) => setVersionLabel(e.target.value)}
          placeholder="e.g. 2026-08-22-v3"
          required
        />
      </Field>
      <Field label="Config (JSON)" hint="Raw JSON — model routing, effort, token ceilings per job type.">
        <Textarea
          value={configText}
          onChange={(e) => setConfigText(e.target.value)}
          rows={10}
          className="font-mono text-xs"
          placeholder={'{\n  "default": { "model": "...", "effort": "medium" }\n}'}
          required
        />
      </Field>
      <Button type="submit" disabled={submitting}>
        {submitting ? 'Publishing…' : 'Publish new config'}
      </Button>
    </form>
  );
}
