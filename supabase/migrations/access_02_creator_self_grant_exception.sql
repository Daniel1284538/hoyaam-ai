-- access_02_creator_self_grant_exception
--
-- Narrow, principled carve-out in fn_prevent_self_grant (trg_matter_access_
-- no_self_grant on matter_access): a user who CREATES a matter is allowed
-- to grant themselves access to that one matter, exactly once, so
-- litigation-create-matter can give the creator working access to their
-- own intake immediately.
--
-- Why this was needed: can_access_matter() gives blanket firm-wide
-- visibility to owner/admin/lawyer only (access_01), but the
-- upload_documents capability that gates matter creation is also held by
-- trainee and clerk -- neither of which has blanket visibility. Without
-- this, a trainee or clerk who creates a matter could create it and then
-- immediately be unable to see it again.
--
-- Why the carve-out stays narrow (does not reopen the self-grant hole this
-- trigger exists to close):
--   1. Scoped to the matter the caller actually created (matters.created_by
--      = new.user_id) -- never any other matter, so an admin/lawyer still
--      cannot self-grant onto a matter someone else created; that path is
--      unchanged and still raises.
--   2. Fires only when NO matter_access row has ever existed for that
--      (matter_id, user_id) pair -- so it is exactly one shot, at/near
--      creation. If an owner/admin later revokes that grant (revoked_at
--      set), the row still exists, so this exception no longer applies --
--      the creator cannot silently re-grant themselves access after an
--      explicit revocation. Re-granting after a genuine revocation still
--      has to go through admin-grant-matter-access like anyone else's
--      access does.
--   3. An ethical_walls entry on the creator for that matter still wins
--      regardless -- can_access_matter() ANDs `not exists(ethical_walls...)`
--      on top of everything else, unaffected by this change.
--
-- Verified against all four cases in-session (transactional test, rolled
-- back, nothing persisted): creator self-grant on own matter -> allowed;
-- same user re-inserting on the same matter -> rejected; self-grant on a
-- matter someone else created -> rejected; ordinary non-self grant
-- (granted_by <> user_id) -> unaffected.

create or replace function public.fn_prevent_self_grant()
returns trigger
language plpgsql
set search_path to 'public'
as $function$
begin
  if new.user_id = new.granted_by then
    if not exists (
      select 1 from matters m
      where m.id = new.matter_id
        and m.created_by = new.user_id
    )
    or exists (
      select 1 from matter_access ma
      where ma.matter_id = new.matter_id
        and ma.user_id = new.user_id
    )
    then
      raise exception 'matter_access: a user cannot grant themselves access (user_id = granted_by)';
    end if;
  end if;
  return new;
end;
$function$;
