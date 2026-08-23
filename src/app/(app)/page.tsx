import { getViewer } from '@/lib/capabilities';
import { NAV_SECTIONS } from '@/lib/nav';
import { Card } from '@/components/ui';

export default async function DashboardPage() {
  const viewer = await getViewer();
  const capCount = viewer?.capabilities.size ?? 0;
  const moduleCount = NAV_SECTIONS.filter((s) =>
    s.items.some((i) => i.anyOf.some((c) => viewer?.capabilities.has(c))),
  ).length;

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Dashboard</h1>
        <p className="mt-1 text-sm text-ink-2">
          Signed in as {viewer?.email} ({viewer?.roles.join(', ') || 'no role'}).
        </p>
      </div>
      <Card title="Overview">
        <dl className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <dt className="text-ink-3">Role(s)</dt>
            <dd className="font-mono text-ink">{viewer?.roles.join(', ') || '—'}</dd>
          </div>
          <div>
            <dt className="text-ink-3">Capabilities held</dt>
            <dd className="font-mono text-ink">{capCount}</dd>
          </div>
          <div>
            <dt className="text-ink-3">Modules visible</dt>
            <dd className="font-mono text-ink">{moduleCount} of {NAV_SECTIONS.length}</dd>
          </div>
        </dl>
      </Card>
      <p className="text-xs text-ink-3">
        Case practice (documents, drafting) and the corpus/templates
        library aren&apos;t shown here — nothing in the backend supports
        them yet (no documents/drafts or corpus tables exist). Only what
        Phases A–E actually built is reachable from this nav.
      </p>
    </div>
  );
}
