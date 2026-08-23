import { createClient } from '@/lib/supabase/server';
import { Card, Badge } from '@/components/ui';
import { RecordRunForm } from './record-run-form';

type EvalSuiteRow = {
  id: string;
  suite_key: string;
  pass_threshold: number;
  gates_publish: boolean;
  [key: string]: unknown;
};

type EvalRunRow = {
  id: string;
  suite_id: string;
  triggered_by: string | null;
  score: number;
  passed: boolean;
  details: unknown;
  created_at: string | null;
  [key: string]: unknown;
};

export default async function EvalsPage() {
  const supabase = await createClient();
  const [{ data: suiteData, error: suiteError }, { data: runData, error: runError }] = await Promise.all([
    supabase.from('eval_suites').select('*').order('suite_key', { ascending: true }),
    supabase.from('eval_runs').select('*').order('created_at', { ascending: false }).limit(50),
  ]);

  const suites = (suiteData ?? []) as EvalSuiteRow[];
  const runs = (runData ?? []) as EvalRunRow[];
  const suiteById = new Map(suites.map((s) => [s.id, s]));

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Quality gates</h1>
        <p className="mt-1 text-sm text-ink-2">
          Eval suites and their recent runs. Suites marked &quot;gates publish&quot; block config/prompt
          publishing when their most recent run failed.
        </p>
      </div>

      <Card title="Record a run">
        <RecordRunForm suites={suites} />
      </Card>

      <Card title="Suites">
        {suiteError && <p className="text-sm text-risk">Failed to load suites: {suiteError.message}</p>}
        {!suiteError && suites.length === 0 && <p className="text-sm text-ink-3">No suites found.</p>}
        <div className="space-y-3">
          {suites.map((s) => (
            <div key={s.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-rule p-3">
              <div>
                <span className="font-mono text-sm text-ink">{s.suite_key}</span>
                <span className="ml-2 text-xs text-ink-3">pass threshold {s.pass_threshold}</span>
              </div>
              <Badge tone={s.gates_publish ? 'warn' : 'default'}>
                {s.gates_publish ? 'gates publish' : 'informational'}
              </Badge>
            </div>
          ))}
        </div>
      </Card>

      <Card title="Recent runs">
        {runError && <p className="text-sm text-risk">Failed to load runs: {runError.message}</p>}
        {!runError && runs.length === 0 && <p className="text-sm text-ink-3">No runs recorded yet.</p>}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-rule text-xs text-ink-3">
                <th className="py-2 pr-3 font-normal">Suite</th>
                <th className="py-2 pr-3 font-normal">Score</th>
                <th className="py-2 pr-3 font-normal">Passed</th>
                <th className="py-2 pr-3 font-normal">Triggered by</th>
                <th className="py-2 pr-3 font-normal">When</th>
              </tr>
            </thead>
            <tbody>
              {runs.map((r) => (
                <tr key={r.id} className="border-b border-rule/60">
                  <td className="py-2 pr-3 font-mono text-xs text-ink">
                    {suiteById.get(r.suite_id)?.suite_key ?? r.suite_id}
                  </td>
                  <td className="py-2 pr-3 font-mono text-xs text-ink-2">{r.score}</td>
                  <td className="py-2 pr-3">
                    <Badge tone={r.passed ? 'seal' : 'risk'}>{r.passed ? 'passed' : 'failed'}</Badge>
                  </td>
                  <td className="py-2 pr-3 font-mono text-xs text-ink-2">{r.triggered_by ?? '—'}</td>
                  <td className="py-2 pr-3 text-xs text-ink-3">{r.created_at ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
