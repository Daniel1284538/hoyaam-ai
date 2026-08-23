import { createClient } from '@/lib/supabase/server';
import { Card, Badge } from '@/components/ui';
import { SetFlagForm } from './set-flag-form';

type FeatureFlagRow = {
  id: string;
  flag_key: string;
  role_id: string | null;
  enabled: boolean;
  updated_by: string | null;
  updated_at: string | null;
};

type RoleRow = {
  id: string;
  label_en: string;
};

export default async function FeatureFlagsPage() {
  const supabase = await createClient();
  const [{ data: flagData, error: flagError }, { data: roleData }] = await Promise.all([
    supabase.from('feature_flags').select('*').order('flag_key', { ascending: true }),
    supabase.from('roles').select('id, label_en'),
  ]);

  const flags = (flagData ?? []) as FeatureFlagRow[];
  const roles = (roleData ?? []) as RoleRow[];
  const roleLabel = new Map(roles.map((r) => [r.id, r.label_en]));

  const byKey = new Map<string, FeatureFlagRow[]>();
  for (const f of flags) {
    const list = byKey.get(f.flag_key) ?? [];
    list.push(f);
    byKey.set(f.flag_key, list);
  }

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Feature flags</h1>
        <p className="mt-1 text-sm text-ink-2">
          Each flag has a global default (no role) plus optional per-role overrides.
        </p>
      </div>

      <Card title="Set flag">
        <SetFlagForm roles={roles} />
      </Card>

      <Card title="Flags">
        {flagError && <p className="text-sm text-risk">Failed to load flags: {flagError.message}</p>}
        {!flagError && byKey.size === 0 && <p className="text-sm text-ink-3">No flags set yet.</p>}
        <div className="space-y-6">
          {Array.from(byKey.entries()).map(([key, rows]) => (
            <div key={key}>
              <h3 className="font-mono text-sm font-medium text-ink">{key}</h3>
              <div className="mt-2 space-y-2">
                {rows
                  .slice()
                  .sort((a, b) => (a.role_id === null ? -1 : b.role_id === null ? 1 : 0))
                  .map((row) => (
                    <div
                      key={row.id}
                      className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-rule p-3 text-sm"
                    >
                      <div>
                        <span className="text-ink">
                          {row.role_id === null ? '— global default —' : roleLabel.get(row.role_id) ?? row.role_id}
                        </span>
                        <span className="ml-2 font-mono text-xs text-ink-3">
                          updated {row.updated_at ?? '—'} by {row.updated_by ?? '—'}
                        </span>
                      </div>
                      <Badge tone={row.enabled ? 'seal' : 'default'}>
                        {row.enabled ? 'enabled' : 'disabled'}
                      </Badge>
                    </div>
                  ))}
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}
