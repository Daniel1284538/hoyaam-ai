import { createClient } from '@/lib/supabase/server';
import { getViewer } from '@/lib/capabilities';
import { Card, Badge } from '@/components/ui';
import { ProposeRuleForm } from './propose-rule-form';
import { ApproveRuleButton } from './approve-rule-button';

export type DeadlineRule = {
  id: string;
  rule_key: string;
  title_ar: string;
  title_en: string;
  trigger_event: string;
  duration_value: number;
  duration_unit: 'calendar_days' | 'working_days' | 'months';
  citation: string;
  status: 'pending_approval' | 'active' | 'superseded' | 'rejected';
  effective_from: string | null;
  effective_to: string | null;
  authored_by: string;
  approved_by: string | null;
  approved_at: string | null;
  created_at: string;
};

const STATUS_TONE = {
  active: 'seal',
  pending_approval: 'warn',
  superseded: 'default',
  rejected: 'default',
} as const;

const DURATION_UNIT_LABEL: Record<DeadlineRule['duration_unit'], string> = {
  calendar_days: 'calendar days',
  working_days: 'working days',
  months: 'months',
};

export default async function DeadlineRulesPage() {
  const [viewer, supabase] = await Promise.all([getViewer(), createClient()]);
  const canPropose = viewer?.capabilities.has('manage_deadline_rules') ?? false;
  const canApprove = viewer?.capabilities.has('approve_rule_change') ?? false;

  const { data, error } = await supabase
    .from('deadline_rules')
    .select('*')
    .order('rule_key', { ascending: true })
    .order('created_at', { ascending: false });

  const rules = (data ?? []) as DeadlineRule[];
  const groups = groupByRuleKey(rules);

  return (
    <div className="max-w-4xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold text-ink">Deadline rules</h1>
        <p className="mt-1 text-sm text-ink-2">
          Litigation deadline computation rules, versioned per rule key. A version becomes
          active only once approved by someone other than its author.
        </p>
      </div>

      {error && (
        <p className="text-sm text-risk">Failed to load deadline rules: {error.message}</p>
      )}

      {canPropose && (
        <Card title="Propose new rule / version">
          <ProposeRuleForm />
        </Card>
      )}

      <div className="space-y-6">
        {groups.length === 0 && !error && (
          <Card>
            <p className="text-sm text-ink-3">No deadline rules yet.</p>
          </Card>
        )}
        {groups.map(([ruleKey, versions]) => (
          <Card key={ruleKey} title={ruleKey}>
            <ul className="space-y-3">
              {versions.map((rule) => (
                <li key={rule.id} className="rounded-md border border-rule p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge tone={STATUS_TONE[rule.status]}>{rule.status}</Badge>
                      <span className="text-sm font-medium text-ink">{rule.title_en}</span>
                      <span className="text-sm text-ink-3">({rule.title_ar})</span>
                    </div>
                    <span className="text-xs text-ink-3">
                      {new Date(rule.created_at).toLocaleDateString()}
                    </span>
                  </div>
                  <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-ink-2 sm:grid-cols-3">
                    <div>
                      <dt className="text-ink-3">Trigger event</dt>
                      <dd className="text-ink">{rule.trigger_event}</dd>
                    </div>
                    <div>
                      <dt className="text-ink-3">Duration</dt>
                      <dd className="text-ink">
                        {rule.duration_value} {DURATION_UNIT_LABEL[rule.duration_unit]}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-ink-3">Citation</dt>
                      <dd className="text-ink">{rule.citation}</dd>
                    </div>
                    {rule.effective_from && (
                      <div>
                        <dt className="text-ink-3">Effective from</dt>
                        <dd className="text-ink">{rule.effective_from}</dd>
                      </div>
                    )}
                    {rule.approved_at && (
                      <div>
                        <dt className="text-ink-3">Approved</dt>
                        <dd className="text-ink">{new Date(rule.approved_at).toLocaleDateString()}</dd>
                      </div>
                    )}
                  </dl>
                  {rule.status === 'pending_approval' && canApprove && (
                    <div className="mt-3 border-t border-rule pt-3">
                      <ApproveRuleButton ruleId={rule.id} />
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </Card>
        ))}
      </div>
    </div>
  );
}

function groupByRuleKey(rules: DeadlineRule[]): [string, DeadlineRule[]][] {
  const order: string[] = [];
  const map = new Map<string, DeadlineRule[]>();
  for (const rule of rules) {
    if (!map.has(rule.rule_key)) {
      map.set(rule.rule_key, []);
      order.push(rule.rule_key);
    }
    map.get(rule.rule_key)!.push(rule);
  }
  return order.map((key) => [key, map.get(key)!]);
}
