-- Litigation Agent Phase 1 — the deterministic half of the deadline
-- design (plan section 03: "Deadlines"). The model is only ever allowed
-- to extract a triggering event and its date; every date shown to a
-- lawyer comes out of this function, applying a signed-off deadline_rules
-- row against the court_calendar. Nothing here is model-computed.
--
-- Egypt's court weekend is Friday/Saturday — used for working_days.
-- Rejects any rule that is not status='active' (i.e. not yet signed off),
-- so this cannot be called until the Phase 0 rules table is populated.

create or replace function public.is_working_day(p_date date)
returns boolean
language sql
stable
set search_path to 'public'
as $$
  select
    extract(dow from p_date) not in (5, 6)  -- 5=Friday, 6=Saturday
    and not exists (
      select 1 from public.court_calendar cc
      where p_date between cc.date_from and cc.date_to
    );
$$;

comment on function public.is_working_day(date) is
  'Egypt court weekend (Fri/Sat) plus any court_calendar row (single-day holiday or court-vacation range). Maintained-by-hand table, per its own comment — no external feed.';

create or replace function public.fn_compute_deadline(p_trigger_date date, p_rule_id uuid)
returns date
language plpgsql
stable
set search_path to 'public'
as $$
declare
  v_rule public.deadline_rules%rowtype;
  v_due date;
  v_remaining integer;
begin
  select * into v_rule from public.deadline_rules where id = p_rule_id;
  if not found then
    raise exception 'deadline rule % not found', p_rule_id;
  end if;
  if v_rule.status <> 'active' then
    raise exception 'deadline rule % is not active (status=%) — only a signed-off rule may compute a real deadline', p_rule_id, v_rule.status;
  end if;

  if v_rule.duration_unit = 'calendar_days' then
    v_due := p_trigger_date + v_rule.duration_value;

  elsif v_rule.duration_unit = 'months' then
    v_due := (p_trigger_date + make_interval(months => v_rule.duration_value))::date;

  elsif v_rule.duration_unit = 'working_days' then
    v_due := p_trigger_date;
    v_remaining := v_rule.duration_value;
    while v_remaining > 0 loop
      v_due := v_due + 1;
      if public.is_working_day(v_due) then
        v_remaining := v_remaining - 1;
      end if;
    end loop;

  else
    raise exception 'unhandled duration_unit %', v_rule.duration_unit;
  end if;

  return v_due;
end;
$$;

comment on function public.fn_compute_deadline(date, uuid) is
  'The deterministic function from plan section 03. Every computed deadline row in public.deadlines should be produced by this, then stored provisional until a lawyer confirms it — never trusted straight from the model.';
