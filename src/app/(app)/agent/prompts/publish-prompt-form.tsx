'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Field, Input, Textarea, Button, Alert } from '@/components/ui';

export function PublishPromptForm() {
  const router = useRouter();
  const [promptKey, setPromptKey] = useState('');
  const [content, setContent] = useState('');
  const [versionLabel, setVersionLabel] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await callEdgeFunction<{ prompt_version_id: string; prompt_key: string; status: string }>(
        'admin-publish-prompt',
        { action: 'publish', prompt_key: promptKey, content, version_label: versionLabel },
      );
      setPromptKey('');
      setContent('');
      setVersionLabel('');
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to publish prompt version.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      {error && <Alert tone="risk">{error}</Alert>}
      <Field label="Prompt key">
        <Input
          value={promptKey}
          onChange={(e) => setPromptKey(e.target.value)}
          placeholder="e.g. deadline_rules"
          required
        />
      </Field>
      <Field label="Version label">
        <Input
          value={versionLabel}
          onChange={(e) => setVersionLabel(e.target.value)}
          placeholder="e.g. 2026-08-22-v2"
          required
        />
      </Field>
      <Field label="Content" hint="Immutable once published.">
        <Textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={10}
          className="font-mono text-xs"
          required
        />
      </Field>
      <Button type="submit" disabled={submitting}>
        {submitting ? 'Publishing…' : 'Publish new version'}
      </Button>
    </form>
  );
}
