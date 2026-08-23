export type NavItem = {
  href: string;
  label: string;
  // Shown if the viewer holds ANY of these capabilities.
  anyOf: string[];
};

export type NavSection = {
  title: string;
  items: NavItem[];
};

// Mirrors capabilities.module in the live matrix (access, rules, agent,
// compliance) — see the Phase E plan: nav is organized by what the
// backend actually groups capabilities under, not the design doc's
// 10-module numbering. Sections with nothing behind them (practice,
// content) are intentionally absent.
export const NAV_SECTIONS: NavSection[] = [
  {
    title: 'Access',
    items: [
      { href: '/access/users', label: 'Users & roles', anyOf: ['manage_users', 'grant_admin_role'] },
      { href: '/access/matters', label: 'Matter access', anyOf: ['grant_matter_access', 'manage_users'] },
      { href: '/access/ethical-walls', label: 'Ethical walls', anyOf: ['manage_ethical_walls'] },
      { href: '/access/break-glass', label: 'Break-glass', anyOf: ['break_glass_access', 'manage_users'] },
    ],
  },
  {
    title: 'Rules',
    items: [
      { href: '/rules/deadlines', label: 'Deadline rules', anyOf: ['manage_deadline_rules', 'approve_rule_change'] },
      { href: '/rules/calendar', label: 'Court calendar', anyOf: ['manage_deadline_rules'] },
    ],
  },
  {
    title: 'Agent governance',
    items: [
      { href: '/agent/configs', label: 'Agent configs', anyOf: ['manage_agent_config'] },
      { href: '/agent/prompts', label: 'Prompt versions', anyOf: ['manage_agent_config'] },
      { href: '/agent/feature-flags', label: 'Feature flags', anyOf: ['manage_agent_config'] },
      { href: '/agent/guardrails', label: 'Guardrails', anyOf: ['manage_agent_config', 'approve_guardrail_change'] },
      { href: '/agent/kill-switch', label: 'Kill switch', anyOf: ['trigger_kill_switch'] },
      { href: '/agent/evals', label: 'Quality gates', anyOf: ['manage_agent_config'] },
    ],
  },
  {
    title: 'Compliance',
    items: [
      { href: '/compliance/audit-log', label: 'Audit log', anyOf: ['view_audit_log'] },
      { href: '/compliance/legal-holds', label: 'Legal holds', anyOf: ['apply_legal_hold'] },
      { href: '/compliance/retention', label: 'Retention policy', anyOf: ['manage_retention'] },
      { href: '/compliance/disclosures', label: 'Disclosures', anyOf: ['manage_disclosures', 'handle_data_request'] },
      { href: '/compliance/data-requests', label: 'Data subject requests', anyOf: ['handle_data_request'] },
      { href: '/compliance/purge', label: 'Purge', anyOf: ['execute_purge'] },
      { href: '/compliance/cost', label: 'Cost & usage', anyOf: ['view_costs'] },
    ],
  },
];
