'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { createClient } from '@/lib/supabase/client';
import { callEdgeFunction } from '@/lib/edge';
import { Button, Card, Field, Input, Alert } from '@/components/ui';

type OpenGrant = { matter_id: string; new_user_id: string };

export function OffboardUserForm() {
  const router = useRouter();
  const [userId, setUserId] = useState('');
  const [grants, setGrants] = useState<OpenGrant[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loadingGrants, setLoadingGrants] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function loadOpenMatters() {
    setLoadError(null);
    setGrants(null);
    if (!userId) return;
    setLoadingGrants(true);
    const supabase = createClient();
    const { data, error: fetchError } = await supabase
      .from('matter_access')
      .select('matter_id')
      .eq('user_id', userId)
      .is('revoked_at', null);
    setLoadingGrants(false);
    if (fetchError) {
      setLoadError(fetchError.message);
      return;
    }
    setGrants((data ?? []).map((row) => ({ matter_id: row.matter_id as string, new_user_id: '' })));
  }

  function updateReassignment(matterId: string, newUserId: string) {
    setGrants((prev) => (prev ?? []).map((g) => (g.matter_id === matterId ? { ...g, new_user_id: newUserId } : g)));
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setLoading(true);
    try {
      const reassignments = (grants ?? [])
        .filter((g) => g.new_user_id)
        .map((g) => ({ matter_id: g.matter_id, new_user_id: g.new_user_id }));
      const result = await callEdgeFunction<Record<string, unknown>>('admin-offboard-user', {
        user_id: userId,
        reassignments,
      });
      setSuccess(JSON.stringify(result));
      setGrants(null);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Offboard failed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Card title="Offboard user">
      <form onSubmit={onSubmit} className="space-y-4">
        <p className="text-sm text-ink-2">
          Reassigns every open matter-access grant the user holds, then
          revokes their roles and disables the account.
        </p>
        {error && <Alert>{error}</Alert>}
        {loadError && <Alert>{loadError}</Alert>}
        {success && <Alert tone="seal">Done: {success}</Alert>}
        <Field label="User ID">
          <Input required value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="user UUID" />
        </Field>
        <Button type="button" variant="secondary" onClick={loadOpenMatters} disabled={!userId || loadingGrants}>
          {loadingGrants ? 'Loading…' : 'Load open matter access'}
        </Button>

        {grants && grants.length > 0 && (
          <div className="space-y-2 rounded-md border border-rule p-3">
            <p className="text-xs font-medium uppercase tracking-wide text-ink-3">
              Reassign each open matter (leave blank to skip)
            </p>
            {grants.map((g) => (
              <div key={g.matter_id} className="flex items-center gap-2">
                <span className="w-64 truncate font-mono text-xs text-ink-2">{g.matter_id}</span>
                <Input
                  placeholder="new user UUID"
                  value={g.new_user_id}
                  onChange={(e) => updateReassignment(g.matter_id, e.target.value)}
                />
              </div>
            ))}
          </div>
        )}
        {grants && grants.length === 0 && (
          <p className="text-sm text-ink-3">No open matter-access grants for this user.</p>
        )}

        <Button type="submit" disabled={loading || !userId} variant="danger">
          {loading ? 'Offboarding…' : 'Offboard user'}
        </Button>
      </form>
    </Card>
  );
}
