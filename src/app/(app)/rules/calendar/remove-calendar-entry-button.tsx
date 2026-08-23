'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Alert } from '@/components/ui';

export function RemoveCalendarEntryButton({ id }: { id: string }) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onRemove() {
    setError(null);
    setLoading(true);
    try {
      await callEdgeFunction('admin-manage-court-calendar', { action: 'remove', id });
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to remove calendar entry.');
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col items-end gap-1">
      {error && <Alert>{error}</Alert>}
      <Button type="button" variant="danger" onClick={onRemove} disabled={loading}>
        {loading ? 'Removing…' : 'Remove'}
      </Button>
    </div>
  );
}
