import { Card, Alert } from '@/components/ui';
import { PurgeFlow } from './purge-flow';

export default function PurgePage() {
  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Purge</h1>
        <p className="mt-1 text-sm text-ink-2">
          Preview a matter before purging it, then confirm the purge separately.
        </p>
      </div>

      <Alert tone="warn">
        HONEST GAP — this only ever deletes the <code className="font-mono">matters</code> stub
        row. No documents/drafts tables exist yet, so there&apos;s nothing else in this system to
        purge.
      </Alert>

      <Card title="Purge a matter">
        <PurgeFlow />
      </Card>
    </div>
  );
}
