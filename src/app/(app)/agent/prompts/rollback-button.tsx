'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Alert } from '@/components/ui';

export function RollbackButton({ versionId }: { versionId: string }) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onClick() {
    setError(null);
    setSubmitting(true);
    try {
      await callEdgeFunction('admin-publish-prompt', { action: 'rollback', to_version_id: versionId });
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to roll back.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <Button type="button" variant="secondary" onClick={onClick} disabled={submitting}>
        {submitting ? 'Rolling back…' : 'Rollback'}
      </Button>
      {error && <Alert tone="risk">{error}</Alert>}
    </div>
  );
}
