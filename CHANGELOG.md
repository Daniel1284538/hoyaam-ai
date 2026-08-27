# Litigation Agent — Change Log

Handoff document for continuing this build in a new Claude Code session/account. Read this before touching anything — it tells you what's real, what's stubbed, and what to never do.

## 2026-08-27 — renderSetNewPassword: fix AAL2-required error on password set

Real bug hit on a real account: setting a new password (via either the
emailed recovery link or an admin-assigned temp password) failed with
Supabase's own error, `AAL2 session is required to update email or
password when MFA is enabled`. Root cause — MFA is mandatory on every
account here (`matters_require_mfa` RLS policy, see `boot()`'s comment
on it), so both the recovery-link session and the admin-temp-password
sign-in land at AAL1 whenever the account already has a verified TOTP
factor, and Supabase's `updateUser({password})` hard-rejects a password
change from below AAL2 — it was never actually possible for a
previously-MFA-enrolled user to reach this screen and succeed before.

`renderSetNewPassword()` now checks the account's factors/AAL before
ever showing the password form: if a verified TOTP factor exists and
the session isn't already AAL2, it walks through the same
challenge/verify step `renderMfaChallenge()` uses on normal sign-in
(entering a TOTP code), which elevates the existing session to AAL2 in
place — then shows the password form as before. A brand-new account
with no verified factor yet (nothing to elevate to) skips straight to
the password form, unchanged from before.

## 2026-08-27 — admin-set-temp-password: closes the manual-SQL gap from the entry below

The previous entry flagged this as the honest next step: there was no
admin-facing function to set someone a temp password, only raw SQL run
by whoever operates the Supabase project directly (Adham Hassan, Ghadi
Youssef, earlier this session). New function `admin-set-temp-password`
closes that.

**Deliberately owner-only, narrower than `manage_users`.** The
permission matrix already grants `manage_users` to both `owner` and
`admin`, and every other user-management function in this build
(`admin-invite-user`, `admin-set-role`, `admin-offboard-user`, etc.)
gates on that capability alone via the shared `requireCapability()`
helper. This one doesn't — it hand-writes a role check for `owner`
specifically, queried directly (`user_roles`, active row, `role_id =
'owner'`), because setting someone else's password directly is a
materially more sensitive action than the rest of what `manage_users`
covers, and this was a deliberate choice to restrict it further, not an
oversight. Using `requireCapability('manage_users', ...)` here would
have been actively misleading for an `admin` who genuinely holds that
capability but is still correctly denied this specific action.

**What it does:** generates a random 16-character password
(`crypto.getRandomValues`, alphabet excludes visually-ambiguous
characters — 0/O, 1/l/I — since this is meant to be read aloud or
retyped by a human), sets it via the Admin API
(`auth.admin.updateUserById`) together with
`user_metadata.must_change_password = true`, and returns the password
**once**, in the response only. It is never written to `audit_log` or
persisted anywhere in plaintext — only the fact that a reset happened is
logged (target user, timestamp, reason if given), same discipline as
every other sensitive action in this build. The `must_change_password`
flag forces the account onto `renderSetNewPassword()` on next login,
before even the MFA check (see the previous entry).

**Frontend:** a new "تعيين كلمة مرور مؤقتة" panel on the Users & Roles
admin page, visible only when `viewer.roleIds` includes `owner` — added
`roleIds` to what `loadViewer()` returns (it previously only exposed a
joined display string, `roleLabel`, not the raw role list a client-side
gate needs). This visibility check is cosmetic; the function's own
server-side role check is the real enforcement, same relationship every
other capability-gated UI element in this app has to its backend. On
success, a dedicated dialog (`openTempPasswordResultDialog`) shows the
password once, with a copy button and an explicit "this will not be
shown again" warning — there is no "view it later" path anywhere, by
design.

**Still doesn't send an email** — see the previous entry's explanation
for why (Supabase's own mailer can't embed an arbitrary generated value
into its fixed templates; a real "email the password" flow needs a
separate provider with its own credentials, which is the firm's decision
to make, not something built unilaterally here). The password from this
function still has to be relayed to the account owner by hand, through a
separate channel, exactly as it was done manually for Adham Hassan and
Ghadi Youssef.

## 2026-08-27 — Self-service password reset, and forced change after an admin-set temp password

Two related gaps, closed together: there was no "forgot password" path at
all (every reset this session — Adham Hassan, Ghadi Youssef — had to be
done manually via raw SQL against `auth.users`, by whoever happens to be
operating the Supabase project directly), and an admin-set temporary
password had no mechanism forcing the user off it — someone could stay
on a password an admin knows indefinitely.

**Self-service reset** (`renderForgotPassword`): a "نسيت كلمة المرور؟"
link on the login screen, using Supabase Auth's own
`resetPasswordForEmail()` — no new Edge Function, this is the standard
supported mechanism, same underlying `auth.users.encrypted_password`
column the manual SQL resets touched, just through its real entry point.
Deliberately shows the same message regardless of whether the address is
actually registered (matches what `resetPasswordForEmail` itself already
declines to reveal) — this can't be used to probe which emails have
accounts.

**Landing back from the emailed link** (`renderSetNewPassword`): Supabase
fires a `PASSWORD_RECOVERY` auth event once it's exchanged the link's
token for a session. The tricky part was ordering: that exchange is
asynchronous, and this app's own hash-based router (`#/dashboard`,
`#/matters/…`) would otherwise try to immediately interpret a leftover
recovery token in `location.hash` as a route, or `boot()` would run first
and flash the ordinary app/MFA screen before the recovery screen ever got
a chance to render. Fixed by checking the URL for a `type=recovery`
marker before calling `boot()` at all, showing a brief "verifying…" state,
and only falling through to normal `boot()` if `PASSWORD_RECOVERY` doesn't
fire within 4 seconds (covers an expired/already-used link — it still
resolves, just via the normal path instead of hanging).

**Forced change after an admin sets a temp password**: a
`user_metadata.must_change_password` flag, checked in `boot()` — before
even the MFA check, on purpose, so nobody enrolls MFA or does anything
else while still on an admin-assigned password. Reuses the exact same
`renderSetNewPassword()` screen as the emailed-reset path; either path
clears the flag via `updateUser({ password, data: { must_change_password:
false } })`. Retroactively set on the two accounts that got a manual
temp password earlier this session (Adham Hassan, Ghadi Youssef), so this
isn't just for future resets.

**What this doesn't cover — still a manual SQL step for now**: there is
no admin-facing "set temp password" UI/Edge Function in this app; that
still means an operator running raw SQL against `auth.users` (as done
twice this session), who should also set the `must_change_password` flag
by hand alongside it, e.g.:
```sql
update auth.users
set raw_user_meta_data = raw_user_meta_data || jsonb_build_object('must_change_password', true)
where id = '<user id>';
```
A real `admin-set-temp-password` Edge Function that does both atomically,
gated on a proper capability and audit-logged like every other admin
action in this build, is the honest next step here — this entry only
closes the login-side half of the gap.

**Operational requirement, not yet verified — no tool access to check
this session**: Supabase Auth validates `resetPasswordForEmail`'s
`redirectTo` against an allowlist (Dashboard → Authentication → URL
Configuration → Redirect URLs). The app passes `location.origin +
location.pathname` dynamically, so no URL is hardcoded — but whatever URL
this file is actually served from still needs to be added to that
allowlist, or the reset email's link will fail. This session has no tool
access to Supabase Auth configuration to check or set that directly —
confirm it in the dashboard before relying on this feature.

## 2026-08-27 — Case analysis survives tab switches, shows its own timestamp

The AI case-analysis result on the Overview tab was disappearing every
time the user switched to another tab and back — it was only ever held
in a local variable inside `renderCaseAnalysisPanel`'s closure, which
gets discarded and recreated from scratch on every mount, forcing a full
re-run (a real Gemini call, real latency, real cost) just to see a
result the user had already generated moments earlier.

Fixed with a module-level `caseAnalysisCache` (`Map<matterId, {result,
generatedAt}>`) that the panel reads from on mount and writes to after
every successful `generate()`. This is deliberately an in-memory,
per-page-load cache — not `localStorage`, not a backend table:
`litigation-analyze-case` itself stays exactly as stateless as it was
designed to be (see the entry below this one — a stored analysis would
silently go stale as documents/the corpus change over time), and this
cache never claims otherwise. It resets on a full reload or logout,
which is intentional, not a shortfall: it's a same-session convenience,
not a record of truth. The result also isn't cached on a failed
request, so a transient error never displays as a false "cached"
analysis on the next visit.

To make sure a cached result never gets mistaken for a fresh one, the
generation timestamp (`new Date()`, captured client-side the moment the
response arrives) is now shown directly above the analysis text —
"تم التحليل في: <date/time>". Staleness is visible, not hidden.

## 2026-08-27 — Editable parties, subject-mode choice at intake, multi-file legal sources, case-analysis roadmap

Four related pieces of work, all in this one session:

**1. Parties are now editable, not just auto-extracted.** New function
`litigation-manage-parties` (`add`/`update`/`delete`), gated the same way
as every other matter-intake-adjacent write (`upload_documents` — see
the HONEST GAP note in its header comment: no dedicated capability for
party management exists yet, and creating one would mean redesigning
who-can-do-what across `litigation-create-matter`, `-update-matter`,
`-record-hearing`, and `-check-conflicts` too, all of which share the
same capability today — a permission-matrix decision, not something to
change unilaterally). `legal-extract` already auto-populates
`matter_parties` from uploaded documents at high confidence (verified
this session by reading the code, not assumed from the old comment) —
this closes the gap where a mis-extracted name or a party added by hand
had no path in. The Parties tab in the frontend is now a real CRUD
panel: add/edit/delete buttons, wired through a shared dialog
(`openPartyDialog`).

**2. Creating a matter now offers a real choice: type the subject, or
upload documents instead.** `litigation-create-matter` gained
`subject_pending_documents: true` — an explicit escape hatch that skips
the subject-required check and leaves the column genuinely `NULL` (never
a fabricated placeholder), so `legal-extract`'s own "only auto-apply into
an empty column" gate still recognizes the field as unset and fills it
the moment a real high-confidence extraction exists. The new-matter
dialog has a two-button toggle ("نص حر" / "رفع مستندات بدلاً من ذلك"); if
the upload path is chosen, the matter is created first, then the
multi-file upload dialog opens immediately against it.

**3. Adding a legal source now accepts multiple files or manual text,
and submission is blocked until at least one has real content.**
`openAddAuthorityDialog` was rewritten: a multi-file input (PDF/image,
any number at once) runs each file through the existing
`admin-ocr-legal-text` OCR-assist function sequentially, producing one
editable, removable "chunk" card per file (verify/correct the
transcription before saving, same discipline as before — OCR output was
never auto-trusted). A persistent manual-text mini-form adds additional
chunks the same way. The Save button stays disabled until
`chunks.some(c => c.chunk_text.trim())` — the literal fix for "the user
should not be able to submit Add Legal Source until he either upload[s]
documents or text."

**4. Case analysis (Overview tab) now includes a roadmap section
grounded in live web search, with the sources it actually checked shown
back to the user.** `litigation-analyze-case` gained a third grounding
source alongside the matter's own documents and the firm's legal corpus:
Gemini's Search grounding tool (`tools: [{ google_search: {} }]`), a real
retrieval mechanism — `groundingMetadata` in the API response is the
API's own record of what was consulted, not the model's self-report, so
`web_sources` (returned to the frontend as `{uri, title}` pairs) and
`web_search_queries` can't be fabricated the way a model's bare claim
"I checked X" could be. Web search is licensed for the roadmap/
next-steps part of the four-part analysis only (facts stay Source-1-only,
law stays Source-2-only); every web-drawn claim in the model's own text
must be inline-tagged `[بحث خارجي]`. The zero-grounding refusal rule is
unchanged and, if anything, reinforced: if neither the matter's documents
nor the legal corpus has anything, the model is still never called —
an open web search alone was deliberately not made sufficient to unlock
an analysis, matching the "zero matches → no model call" rule used
everywhere else in this system. The Overview panel now shows the actual
external sources consulted (as links) and the search queries used when
any were, and says explicitly when none were — the empty case is
reported, not silently omitted.

Redeployed and smoke-tested (`litigation-analyze-case` v3: 401 on an
unauthenticated call, not 500 — confirms the redeploy didn't leave the
function in a broken state after an earlier failed deploy attempt hit a
string-escaping syntax error during manual JSON transcription).

**Known gaps, not fixed here:**
- No dedicated capability for party management or matter creation — see
  point 1 above.
- Web search grounding is best-effort per Gemini's own request-time
  choice — `web_sources` can legitimately come back empty even when the
  tool is available and the analysis otherwise succeeded; this is now
  reported honestly in the UI rather than looking like a missing feature.
- No tool access in this session to Edge Function *secrets* — the
  `GEMINI_MODEL` fallback used throughout this codebase is
  `gemini-3.6-flash`, but an explicit secret override on the project
  would take precedence over that and isn't something this session could
  check or change.

## 2026-08-27 — Gemini model migration: gemini-2.5-flash → gemini-3.6-flash

Every AI feature stopped working: `Gemini API returned 404: This model
models/gemini-2.5-flash is no longer available to new users.` Verified
against Google's own current model listing (not assumed) —
`gemini-2.5-flash` is being phased out and `gemini-3.6-flash` is real,
current, and exactly what Google's own error message pointed to.

**Every function that actually calls `generateContent` was on
`Deno.env.get('GEMINI_MODEL') || 'gemini-2.5-flash'`** — 11 in total,
found by checking every deployed function individually rather than
guessing from filenames (embedding-only functions use
`gemini-embedding-001`, a separate model lifecycle, and were correctly
left untouched): `litigation-research`, `litigation-chronology`,
`litigation-memo`, `litigation-draft`, `legal-extract`,
`litigation-analyze-case`, `litigation-hearing-briefing`,
`litigation-summarize`, `litigation-check-conflicts`,
`litigation-review-draft`, `admin-ocr-legal-text`. All 11 redeployed with
the fallback changed to `gemini-3.6-flash`.

**Important limitation, read before assuming this is fully fixed:** there
is no tool access in this session to Supabase Edge Function *secrets*
(only the CLI or dashboard can list/set them). If a `GEMINI_MODEL`
environment secret is explicitly set on the project (rather than relying
on the in-code fallback), it still overrides everything above and this
fix does nothing for it — check Project Settings → Edge Functions →
Secrets, and update it there too if present.

**Repo hygiene done at the same time, since every one of these files was
already open:**
- 5 of the 11 (`litigation-hearing-briefing`, `litigation-summarize`,
  `litigation-check-conflicts`, `litigation-review-draft`,
  `admin-ocr-legal-text`) had **no source in this repo at all** — a
  standing gap flagged in the CORS-fix entry above. They're now vendored
  in `supabase/functions/` like everything else.
- Every one of the 11 was also redeployed with the current canonical
  `_shared/admin.ts` (the longer, documented version with `GET, POST,
  OPTIONS` CORS), replacing whichever older bundled copy each one had —
  closing drift the repo's own handoff notes have called out since the
  CORS-fix session.

**Verified**, not assumed: fetched each function's live source individually
before patching (not just grepped local files, several of which don't
exist locally), and swept every other deployed function for any
`generateContent` call I might have missed — none. After redeploying, a
POST smoke-test against all 11 confirms each boots cleanly (`401` missing-
auth, not a `500` crash) — full generation behavior needs a real session
to verify end-to-end, but nothing in the deploy pipeline broke.

## 2026-08-27 — mandatory case fields, subject fix-up path, AI case analysis, multi-file upload

Closes the gap that surfaced the mandatory-MFA bug earlier today: a matter
had been created with no subject, and there was no way to add one
afterward short of editing the database directly.

**Matter creation now requires every field except parties**
(`matter_label`, `court`, `circuit`, `case_number`, `case_year`,
`matter_type`, `stage`, `subject`) — `litigation-create-matter` validates
all of them server-side (v5); the New Matter dialog mirrors the same list
client-side so the error surfaces before a round trip, not after. No DB
`NOT NULL` constraint was added — existing matters with real nulls in
these columns (Adham's included) are legitimate historical state, not
something to paper over with a fabricated backfill value, so this governs
new intake only.

**`litigation-update-matter`** (new) is the fix-up path — add or correct
any of those fields on a matter that already exists, without needing to
know or re-enter everything else. Only keys actually sent are touched; a
key set to `null` explicitly clears it, distinct from omitting it. Gated
on `upload_documents` (same HONEST GAP as create — no dedicated capability
exists) *and* `can_access_matter`, since holding the capability generally
isn't the same as having access to this specific matter. Wired into the
Overview tab as "تعديل بيانات القضية" / "+ إضافة الموضوع (نص)".

**`litigation-analyze-case`** (new) — manual, on-demand AI case analysis,
run from a button in the Overview tab, deliberately refusing to run until
`subject` is filled in (both server-side and as a disabled button
client-side). Two grounding sources, same anti-fabrication discipline as
every other AI feature here:
- This matter's own extracted document pages (same source
  `litigation-chronology` reads) — the model is told these are the *only*
  source of case facts.
- A hybrid search of the legal-authorities corpus using the subject as the
  query (same `fn_search_authorities` RRF path `litigation-research`
  uses) — the model is told these are the *only* source it may cite law
  from, with the same repealed-law/`[UNVERIFIED]`/`[DISPUTED]`/`[FIQH]`
  handling.

If *neither* source has anything, the model is never called — same
"zero matches → no model call" rule as `litigation-research`, for the same
reason: there'd be nothing to ground an answer in but the bare subject
sentence and the model's own memory. Not persisted (regenerate on demand,
same choice as chronology/hearing-briefing/summarize — case documents and
the corpus both change over time).

**Upload now takes multiple files at once** — `openUploadDialog` accepted
exactly one file per invocation; it now accepts any number, processed
sequentially (predictable load, simple error handling), with a per-file
status row (queued/رفع/استخراج/تم/فشل) replacing the old single-file
3-step visual stepper, which doesn't generalize to N files. One file
failing doesn't stop the rest of the batch. Reachable from both the
Documents tab and directly from the Overview tab's subject panel — added
there specifically so a lawyer without a subject yet in hand can upload
the case file instead, and the case analysis above can draw on it once a
subject is entered.

**Real pre-existing bug found and fixed while touching this**: `h()` had
no special case for `<textarea>` `value:` binding — `setAttribute('value',
…)` is a silent no-op for a textarea's *displayed* content, which comes
from its child text node / `.value` property instead. This affected the
generic back-office `buildForm` helper's `textarea` field type (used
across several admin dialogs) and would have broken the new edit-subject
dialog outright — it exists specifically to show the *current* subject for
editing, and would have rendered blank instead. Fixed once in `h()` for
every textarea; also fixed two `<select>` elements in the new dialogs that
had the same bug in a different shape (a `<select>`'s displayed selection
isn't attribute-driven either) by using the already-established
per-`<option>` `selected:` pattern from elsewhere in this file, rather than
extending `h()` again for a case with no other call sites yet.

**Disclosed, not fixed**: the per-field "X مطلوب" validation error message
is a dynamic template string (`` `${missing[1]} مطلوب` ``) and does not
translate in English mode — same class of gap the English/Arabic toggle
changelog entry already disclosed for ~30 other dynamic strings; extend
`EN_DICT` plus array-child restructuring if it matters enough to close.

## 2026-08-27 — mandatory-MFA gate never actually fired for a brand-new user (real bug, fixed)

`boot()`'s MFA gate was `if (aal.nextLevel === 'aal2' && aal.currentLevel !== 'aal2')` —
copied from the Supabase docs' "step up an existing MFA-enabled user" pattern,
not a "force first-time enrollment" pattern, and those are different things.
`nextLevel` only becomes `'aal2'` once the account already has a *verified*
factor; for a brand-new account with zero factors, `nextLevel` stays `'aal1'`
— equal to `currentLevel` — so the whole gate silently evaluated false and a
freshly invited user sailed straight past MFA into the app on nothing but
email+password. `renderMfaEnroll()` (line ~4258, the only place it's ever
called) was consequently dead code for exactly the case it most needed to
run: first-time enrollment.

Caught on a real account, not by inspection alone: user signed in, landed in
the app normally, no MFA prompt — confirmed against the DB that
`auth.mfa_factors` was empty and the session's only AMR entry was
`password`, i.e. genuinely stuck at aal1 with no way in the UI to reach
aal2, since `renderMfaEnroll()` has no other entry point anywhere in the
app. The user could still *create* a matter (writes go through service-role
Edge Functions, which bypass RLS entirely) but couldn't see it afterward —
`matters_require_mfa`, a RESTRICTIVE policy ANDed under every matter SELECT,
silently returns zero rows rather than an error once RLS excludes
everything, which is what made this look like an access-grant bug at first
rather than an MFA-gate bug.

**Fix:** `boot()` now calls `listFactors()` first and decides purely from
that — no verified TOTP factor on the account at all → force
`renderMfaEnroll()` regardless of what `nextLevel`/`currentLevel` say;
verified factor exists but this session hasn't stepped up →
`renderMfaChallenge()` (unchanged). `getAuthenticatorAssuranceLevel()` is
only consulted after a verified factor is confirmed to exist.

Also hardened `renderMfaEnroll()` itself while in there: it unconditionally
called `enroll()` on every mount, so a user who opened the QR screen and
reloaded/abandoned it before scanning would leave a stale *unverified*
factor behind, and the next `enroll()` call would fail against it ("factor
already exists") with no way to recover from the UI. It now clears any
leftover unverified TOTP factor first.

No backend/RLS change — `matters_require_mfa` and `mfa_verified()` are
correct and untouched; the bug was purely in when the frontend chose to
show the enrollment screen. This is a `litigation-agent.html`-only fix; per
the standing note in this file, upload the updated file wherever the
litigation product is actually hosted — there's no deploy pipeline this
session controls for it.

## 2026-08-27 — matter creator gets automatic access on intake

`litigation-create-matter` previously created a matter and gave the caller
zero access to it afterward. That was silently fine for `owner`/`lawyer`
(blanket firm-wide visibility via `can_access_matter()`, `access_01`), but
`upload_documents` — the capability that gates matter creation — is also
held by `trainee` and `clerk`, neither of which has blanket visibility.
Concretely: a trainee or clerk could create a matter and then immediately be
unable to see the matter they'd just made.

**Fix isn't just "insert a `matter_access` row" — that alone is rejected.**
`trg_matter_access_no_self_grant` (`fn_prevent_self_grant`) already blocks
any `matter_access` insert where `user_id = granted_by`, by design, to stop
an admin/lawyer from self-service-granting themselves onto a matter someone
else created. `access_02_creator_self_grant_exception.sql` adds a narrow
carve-out to that trigger instead of removing it: a self-grant is allowed
only when (a) the grantee is that specific matter's own `created_by`, and
(b) no `matter_access` row has ever existed for that `(matter_id, user_id)`
pair. (b) is what keeps this to exactly one shot — if an owner/admin later
revokes the grant, the row still exists, so the creator cannot silently
re-grant themselves afterward; a genuine re-grant still has to go through
`admin-grant-matter-access` like anyone else's. An `ethical_walls` entry on
the creator for that matter still wins regardless, unaffected by this
change — `can_access_matter()` ANDs it in on top of everything else.

`litigation-create-matter` now inserts that self-grant right after creating
the matter (before parties), with its own `matter_access_granted` audit
entry alongside the existing `matter_created` one. A failure past matter
creation (the access grant, or parties) is reported as `207` with the
`matter_id` and what partially failed, rather than silently dropped —
extending the pattern the parties-insert error path already used.

**Verified**, not assumed: a transactional test (rolled back, nothing
persisted) confirmed all four cases — creator self-grants on their own new
matter (allowed), the same user tries a second self-grant on the same
matter simulating a post-revocation re-grant (rejected), a user self-grants
on a matter someone *else* created (rejected — the carve-out doesn't
generalize), and an ordinary non-self grant with `granted_by != user_id`
(unaffected, still works exactly as before).

## 2026-08-23 — CORS fix: 23 back-office `admin-*` Edge Functions

The entire back-office console was write-dead from the browser. Every button
in it failed with an opaque "Failed to fetch" — no status, no body, nothing in
the network tab beyond a red preflight.

**Root cause:** Supabase does **not** add CORS headers to custom Edge Functions
the way it does for REST/Auth/Storage. The `litigation-*`/`legal-*` lineage had
`corsPreflight()` in its `_shared/admin.ts` and worked; the 23 back-office
`admin-*` functions were deployed from a copy of that helper predating the CORS
addition, so the browser's preflight `OPTIONS` hit `if (req.method !== 'POST')
return badRequest('POST only')` and got a `400` with no
`access-control-allow-origin`. The browser then refused to send the real POST.
Confirmed before fixing with a direct `curl -X OPTIONS` on each.

**Not** a capability/auth bug — worth stating, because the first read of the
symptom looked like one. These 23 already carried the *fixed*, non-throwing
`requireCaller`/`requireCapability` (the ones that `return` a Response rather
than throwing it into `Deno.serve`'s generic-500 path). Only `bootstrap-owner`
and `bootstrap-owner-link` still bundle the old throwing version. So this was a
pure transport fix with no change to auth control flow — deliberately, since
swapping helper *semantics* under callers written for the other convention is
exactly how you turn a broken button into a silently-unguarded one.

**Fix**, applied identically to all 23: `corsPreflight` added to the import
list, and two lines at the very top of `Deno.serve`, before the method check:

```ts
const preflight = corsPreflight(req);
if (preflight) return preflight;
```

plus the CORS-carrying `_shared/admin.ts` (`CORS_HEADERS` spread into both
`AdminError` and `json()`, so error responses carry them too — a 403 the
browser can't read is as useless as no response). `Access-Control-Allow-Methods`
is `GET, POST, OPTIONS`: `admin-list-matters-for-grant` is GET-only, and a
`POST, OPTIONS`-only header would have left exactly that one still broken.
The canonical `supabase/functions/_shared/admin.ts` in this repo was updated to
match, so the two lineages stop drifting.

Functions fixed: `admin-set-role`, `admin-invite-user`,
`admin-grant-matter-access`, `admin-manage-ethical-wall`,
`admin-break-glass-grant`, `admin-revoke-sessions`, `admin-offboard-user`,
`admin-list-matters-for-grant`, `admin-propose-deadline-rule`,
`admin-approve-deadline-rule`, `admin-manage-court-calendar`,
`admin-manage-retention-policy`, `admin-apply-legal-hold`,
`admin-publish-agent-config`, `admin-publish-prompt`, `admin-set-feature-flag`,
`admin-guardrail-change`, `admin-kill-switch`, `admin-record-eval-run`,
`admin-set-spend-limit`, `admin-manage-disclosures`, `admin-data-request`,
`admin-execute-purge`.

**Verified**, not assumed: a preflight sweep over all 23 returns `204` with
`access-control-allow-methods: GET, POST, OPTIONS` (23/23), and a real
unauthenticated POST now returns a readable `401` carrying
`access-control-allow-origin: *` instead of dying at the preflight. A second
sweep over every *other* deployed function confirms the only four still without
CORS are the ones deliberately left alone: `bootstrap-owner` /
`bootstrap-owner-link` (one-time, self-disabling, invoked by curl and never by
the browser) and `temp-owner-password-reset` / `tmp-gemini-check` (already
neutralized to `410 Gone`).

**Standing gap this exposed — read before touching the back-office again:**
the 23 `admin-*` back-office functions have **no source in this repo**. They
were built and deployed directly in a parallel session, so the deployed version
is their only source of truth, and a patch like this one can only be applied by
fetching each function, editing, and redeploying. Anyone doing further
back-office work should vendor those sources into `supabase/functions/` first.

## 2026-08-23 — English/Arabic language toggle

EN/AR toggle in the top bar (persisted to `localStorage` as `litigation-agent-lang`), covering the whole app including the admin/back-office section.

**Scope — UI chrome only, never data.** Nav, buttons, page titles, table headers, empty states, form labels, static help text all translate. Matter subjects, party names, extracted document text, AI-generated drafts, and search snippets never do — translating a legal record would risk altering what it actually says, the exact fabrication risk this build has refused all session (see the corpus-fabrication refusal above). If a future session is tempted to add real translation of case *content*, don't, for the same reason.

**How it works (read this before touching UI strings):** `h()` — the DOM-builder every screen in this file uses — now runs every literal string child, and every `placeholder`/`title` attribute, through `t()`, which looks it up by **exact text match** in `EN_DICT` (~580 entries) when English is selected. This is why the ~560 static Arabic strings translate everywhere already, with zero changes to the hundreds of call sites that build the UI — including strings that only exist as values in label maps like `STAGE_LABELS`/`VERIFICATION_LABELS`, since those already flow through `h()` as children. Unrecognized text (which includes 100% of real data) passes through unchanged. **Consequence for future edits: any new static Arabic UI string needs a matching entry added to `EN_DICT`, or it just won't translate — it won't error, it'll silently stay Arabic in English mode.**

Locale-aware now via `localeTag()`: `fmtDate()` and every `toLocaleDateString`/`toLocaleString` call. Direction flips via `<html dir="rtl|ltr">`, which the entire stylesheet respects automatically because it's built with logical CSS properties (`padding-inline`, `inset-inline-*`, `border-inline-*`) with zero hardcoded `left`/`right` — confirmed by grep before relying on it. One real bug caught in that audit: `body` had a hardcoded `direction: rtl` that would have silently overridden the toggle; removed.

**Known residual gap:** ~30 of the highest-traffic dynamic (template-literal) strings — repeated error-message prefixes, matter-tab counts, overdue/remaining day counts, repealed-date chips — were restructured into array children so they translate too. A handful of lower-traffic ones (some admin toast confirmations, rare fallback labels assembled via template literals) were not, and will still show Arabic text when English is selected. This is disclosed, not silently accepted as "the same as before" — extend `EN_DICT` plus the same array-child restructuring pattern to close them if it matters.

## 2026-08-23 — deterministic template-fill drafting (no AI)

Closes the last item left open by the design-mockup audit below: a third drafting path, `litigation-fill-template`, alongside the two AI ones (`litigation-draft`, `litigation-memo`).

- Plain `{{variable}}` substitution into a template's `content_text` — zero model calls, so nothing to fabricate and nothing to cite. Variable set: `matter_label`, `court`, `circuit`, `case_number`, `case_year`, `stage`, `subject`, `plaintiff_name`, `defendant_name`, `next_hearing_date`, `lawyer_name`, `date` (all from real columns — `matters`, `matter_parties`, the nearest upcoming `hearings` row, the caller's own `profiles` row), plus `claim_amount`/`opponent_address`/`court_address`, which have no backing column anywhere in this schema and so are **always** a visible `[يُستكمل يدوياً]` placeholder unless the lawyer types one in.
- What's persisted is recomputed server-side from a fresh template fetch + the submitted variables — never the client's own rendered text — so a saved draft is provably "this template + these variables."
- Frontend: "+ ملء قالب (بلا ذكاء اصطناعي)" in the Drafts tab. Live preview is pure client-side string substitution (no round trip per keystroke), built as real DOM text nodes rather than `innerHTML` since the content includes database- and user-sourced text. Print isolates the preview via `#print-target` + an `@media print` rule rather than printing the whole app shell.

This was the last of the three "smaller, genuinely portable" items flagged in the audit — all are now closed (dashboard/notifications/roll/deadlines, archive search filters, and this).

## 2026-08-23 — archive search: court filter + quick-search chips

Closed one of the two "smaller, genuinely portable" items left open by the design-mockup audit below.

- `litigation-search-archive` now accepts an optional `court` filter, and its response carries `matter_label`/`court`/`circuit`/`case_number`/`case_year` so result cards show real case context instead of just a filename. Filtering happens client-side in the function (after an overfetch) rather than as a two-level-deep PostgREST embed filter, which isn't proven to work reliably.
- **No `doc_type` filter** — the mockup had one (عقود/مذكرات/صحف/أحكام), but `documents` has no column for it and nothing in this pipeline classifies a filing by legal document type. Adding a dropdown for it would filter over data that doesn't exist; skipped rather than faked.
- Frontend: the court dropdown is sourced from a live, RLS-scoped `select distinct court from matters` — not a hardcoded guess at which courts this firm's archive spans. 5 quick-search chips are hardcoded example query text (safe: they're just click-to-try search terms, not a claim about the archive's actual contents, unlike the mockup's fabricated "AI summary" badges which stay excluded).

Still open: the deterministic non-AI "fill template variables, live preview, print" fast path in drafting, mentioned in the same audit — not built yet.

## 2026-08-23 — design-mockup feature audit: dashboard, notifications, hearing roll, deadlines

Re-checked the `hoyaam--main` design mockup (the same React/Vite demo used earlier for the visual design system) feature-by-feature against the live portal, not just visually. Found and closed 3 real gaps, all pure frontend + existing backend (no new migrations/functions):

- **`#/dashboard`** (new default route) — 5 stat tiles, today's hearing timeline, top provisional deadlines.
- **Notifications bell** (in the top bar on every screen) — hearings + deadlines due within 3 days, sorted, inline confirm action.
- **`#/roll`** — firm-wide hearing calendar + day agenda. Notably, `litigation_02_matter_case_schema.sql`'s own comment on the `hearings` table said this was planned ("the one rolling roll across every matter") but nothing had built it until now.
- **`#/deadlines`** — firm-wide version of the existing per-matter deadlines tab, grouped overdue/this-week/upcoming.

RLS already scopes every query to matters the viewer has access to, so unlike the mockup there's no separate "mine only" toggle needed — there's no `assigned_to`/responsible-lawyer column on `matters` either, so that mockup filter couldn't have been ported literally anyway.

**Deliberately not ported, with reasons:**
- The mockup's per-search-result "AI summary" / "AI precedent" badges are hardcoded fake text, not real model output. Porting them verbatim would be exactly the fabrication this build has refused everywhere else. `litigation-search-archive` / `litigation-research` stay honest instead.
- Bounding-box highlighting on the scanned document page in Review (showing exactly where an extracted field was found) — `document_pages.layout jsonb` exists in the schema but nothing populates it, and `legal-extract`'s Gemini call doesn't request per-field coordinates today. Doable, but a real pipeline change, not a quick port — flagged as a future item.
- Quick-search chips and doc-type/court filter dropdowns on Archive Search, and a deterministic non-AI "fill the template variables, live preview, print" fast path in the drafting flow (as a complement to the existing AI-drafted `litigation-draft`/`litigation-memo`) — both genuinely portable and safe, just not built this round; smaller, separate follow-ups if wanted.

## 2026-08-23 — reconciled with a parallel session's build

A **separate** Claude Code session, working in a different repo (`Daniel1284538/hoyaam-ai`) against this **same** Supabase project, independently built the legal-research half of the plan this session had left unbuilt: corpus provenance/repeal tracking, real hybrid search (dense + Arabic FTS fused with Reciprocal Rank Fusion), a human-verification workflow for corpus entries, and merged the entire back-office admin console into one more advanced frontend. Both sessions converged on the same architecture independently (Supabase Edge Functions called from a single HTML file — no Next.js API layer in either).

The user was shown a point-by-point reconciliation and chose to **adopt that session's frontend as the frontend of record here**, rather than duplicate its work. What changed in this repo as a result:

- **`litigation-agent.html`** now contains that session's `index.html` (2807 lines, includes the full back-office admin console under `#/admin/*`, MFA/TOTP enrollment, and the corpus verification UI) — it replaces, not extends, what this session had built. The filename is unchanged (this repo's root `index.html` is the unrelated PropTech staff portal — see below — so the litigation product keeps living at `litigation-agent.html`).
- **`superseded/litigation-agent-original.html`** — this session's own pre-reconciliation frontend, kept for reference/diffing, not loaded anywhere.
- **3 new migrations** (`corpus_01_provenance_and_repeal`, `corpus_02_hybrid_search`, `corpus_03_pin_search_path`) and **2 new Edge Functions** (`admin-verify-authority`, `admin-embed-authorities`) pulled down from live Supabase into this repo as local source-of-record — they were already applied/deployed by the other session, not newly written here.
- **`litigation-research`** and **`litigation-manage-authority`** source refreshed to the versions the other session deployed (v4 each) — the old copies in this repo were superseded on the live project after this session's own v3/v4 CORS-fix deploys.

Everything else below this point (schema/function/frontend descriptions from the original build) is **historical** — left in place so the reasoning behind each decision is preserved, but treat the sections above as authoritative for current file contents.

## 2026-08-23 — Citation & Provision Inspector + Legal Memo Builder

The two items flagged above as genuinely open (checked against both frontends) are now built, in this repo:

- **Citation & Provision Inspector** — a "فحص الاستشهادات" button on every row in the Drafts tab opens a modal listing that draft's `draft_citations`, each rendered side-by-side: the citation text as it appears in the draft vs. the verbatim `chunk_text` of the `authority_chunks` row it's bound to (plus the source's verification/repeal chips). New Edge Function **`litigation-verify-citation`** (capability `export_matter`) does the write: marks a citation `verified` or `flagged`, refuses `verified` if the citation isn't bound to a real retrieved chunk (nothing to check it against), and logs to both `audit_log` and `review_actions` — matching `litigation-review-extraction`'s pattern. This is the piece that actually resolves the unverified/flagged citations `litigation-export-draft` was already blocking export on.
- **Legal Memo & Analysis Builder** — new Edge Function **`litigation-memo`** (capability `generate_draft`), a workflow distinct from `litigation-draft`: given a matter and a legal question, it retrieves via `fn_search_authorities` and, only if that retrieval finds something, asks Gemini for a structured IRAC memo (Issue/Rule/Application/Conclusion) grounded strictly in the retrieved passages. Zero matches → no model call, no draft created, same discipline as `litigation-research`. Every citation the model reports using is bound immediately to its real `authority_chunk_id` (status inherited from that authority's own `verification_status` — a disputed source lands pre-flagged). Persisted as a `drafts` row (`doc_type='legal_memo'`) so it's exportable via the existing `litigation-export-draft` for free. Frontend: "+ إنشاء مذكرة قانونية" button in the Drafts tab opens the question/filters dialog; memos show up in the same drafts table as regular drafts.
- Both are empty-corpus-safe by construction (same discipline as everything else in this build): with no authorities loaded yet, the Inspector shows "no citations", and the Memo Builder always returns its "no results" note rather than ever inventing one. They'll do real work automatically once the corpus has content.
- Neither needed a new migration — both build entirely on existing tables (`drafts`, `draft_citations`, `authority_chunks`) and the `fn_search_authorities` RPC.

## What this project is

An Arabic-first litigation-support assistant for one Egyptian law firm, built per the plan artifact "Litigation Agent Build Plan" (published earlier in the originating session — ask the user for the link if you need the full plan again). Single-file frontend (`litigation-agent.html`), Supabase backend (Postgres + Edge Functions), same architecture as this repo's existing staff portal (`index.html`).

This repo (`01015523142az-hash/project`, branch `claude/egypt-lawyer-ai-agent-u4h1qb`) also contains an unrelated, already-deployed staff portal — `index.html` / `sw.js` / `CNAME` — do not touch those, they're a separate live product.

## Supabase project

- Project ref: **`xjxzkjyotbumxtddkepd`** ("Daniel1284538's Project", region `eu-west-1`, Postgres 17.6)
- This is the **same project** used by a separate back-office admin portal (`hoyaam-ai`, a Next.js app, different repo entirely — `Daniel1284538/hoyaam-ai` on GitHub). That portal's Phase A–E build (roles, capabilities, RLS helper functions, ~25 `admin-*` Edge Functions) is the substrate the litigation agent is built on top of. Don't be surprised to see `admin-*` functions and back-office tables (`disclosures`, `legal_holds`, `retention_policies`, `agent_configs`, etc.) in the same project — they're a different product sharing the DB.
- Owner test account: `ghadi.yousssef@gmail.com`, role `owner` (holds every capability this system checks), MFA already enrolled. Same login works on both the litigation agent and the back-office portal (same Supabase Auth users).
- **`GEMINI_API_KEY`** must be set as an Edge Function secret (Supabase Dashboard → Project Settings → Edge Functions → Secrets) for any AI-calling function to work (`legal-extract`, `litigation-chronology`, `litigation-draft`, `litigation-research`). Without it they fail loudly with a clear error — by design, not a bug.

## Repo layout (what's new in this session)

```
litigation-agent.html          — the whole frontend (single file, RTL, vanilla JS)
supabase/migrations/           — the 8 SQL migrations that built the DB schema (see below)
supabase/functions/            — source for all 15 litigation-specific Edge Functions
supabase/functions/_shared/admin.ts — shared helper, canonical copy (see note below)
CHANGELOG.md                   — this file
```

**Important:** `supabase/migrations/*.sql` and `supabase/functions/*/index.ts` were written to this repo as a **record of what was applied directly to the live Supabase project via MCP tools** (`apply_migration` / `deploy_edge_function`) — they were not run through `supabase db push` / `supabase functions deploy` from this repo. Everything in them is already live on `xjxzkjyotbumxtddkepd`. If you set up the Supabase CLI in the new session, treat these as the migration history to reconcile with (`supabase migration repair` or equivalent), not as pending work.

Each deployed function actually has its own embedded copy of `_shared/admin.ts` (that's how `deploy_edge_function` bundles work — no shared-file support across functions in a single call). The copy at `supabase/functions/_shared/admin.ts` in this repo is the canonical source; if you edit it, you must re-propagate the change into every function and redeploy each one individually — there's no automatic sharing unless you switch to the Supabase CLI's local dev workflow, which does support `../_shared/` imports natively (these files are already written with that import path, so `supabase functions deploy` from this repo would work as-is).

## Database schema — 8 migrations (litigation_01 through litigation_08)

Applied in order, on top of the pre-existing back-office schema (which already had stub tables — see comments in `litigation_02` — anticipating this build):

1. **`litigation_01_extensions`** — `vector`, `pg_trgm`, `unaccent` extensions
2. **`litigation_02_matter_case_schema`** — ALTERs the `matters` stub with real columns (court, circuit, case_number, case_year, matter_type, stage, subject, opened_at); adds `matter_parties`, `hearings`, `deadlines` tables
3. **`litigation_03_documents_pipeline`** — `documents`, `document_pages`, `document_chunks` (with `embedding vector(1536)`, currently unpopulated), `extractions`
4. **`litigation_04_corpus_and_templates`** — `authorities`, `authority_chunks`, `templates`; creates the `templates` storage bucket
5. **`litigation_05_drafting_and_audit`** — `drafts`, `draft_citations`, `agent_runs`, `review_actions`
6. **`litigation_06_deadline_engine`** — `is_working_day()` and `fn_compute_deadline()` — the deterministic date-math functions. `fn_compute_deadline` **refuses to run** against any `deadline_rules` row that isn't `status='active'`
7. **`litigation_07_rls_policies`** — RLS on every new table, following the existing project convention exactly: a `RESTRICTIVE` `mfa_verified()` policy ANDed with a `PERMISSIVE` access-based `SELECT` policy. **No INSERT/UPDATE/DELETE policies anywhere** — every write goes through a service-role Edge Function with its own `requireCapability()` check
8. **`litigation_08_extension_schema_fix`** — moved `vector`/`pg_trgm`/`unaccent` out of the `public` schema into `extensions` (advisor-flagged security warning, fixed)

Storage buckets already existed pre-session: `matter-documents`, `matter-drafts`, `authorities` (all private, no `storage.objects` RLS policies for `authenticated` — access is exclusively via `sign-document-url` / signed upload URLs from Edge Functions). `templates` bucket added in migration 4.

## Edge Functions — 15 deployed (all in `supabase/functions/`)

All follow the same pattern as the back-office `admin-*` functions: `requireCaller` (resolves the JWT), `requireCapability` (checks `has_capability()` under the caller's own auth context, audits denials), service-role client for the actual write, `logAction` for the audit trail. **Every helper returns a `Response`, never throws** — `Deno.serve()` has no special handling for thrown `Response` objects, it collapses them to an opaque 500. This was a real bug fixed earlier in the back-office build; don't reintroduce it.

**CORS — read this before adding a new function.** None of these functions get automatic CORS handling from Supabase (that's a documented gap for custom Edge Functions, unlike REST/Auth/Storage). Every function **must** call `corsPreflight(req)` first thing inside `Deno.serve()` and return early if it returns non-null, or every browser call fails as an opaque "Failed to fetch" with zero diagnostic info. This was a real production bug hit and fixed mid-session — all 15 functions have the fix; **any new function needs it too**.

| Function | Purpose | Capability gate |
|---|---|---|
| `litigation-create-matter` | Manual matter intake | `upload_documents` |
| `legal-ingest` | Step 1 of upload: creates `documents`/`ingestion_jobs` rows, mints signed upload URL | `upload_documents` |
| `legal-extract` | Step 2: downloads file, calls Gemini for OCR + structured extraction, confidence-gated auto-apply | `upload_documents` |
| `litigation-review-extraction` | Human confirm/correct/reject of low-confidence extractions | `review_extractions` |
| `litigation-record-hearing` | Manual hearing-roll entry | `upload_documents` |
| `litigation-propose-deadline` | Creates a provisional deadline via `fn_compute_deadline` | `confirm_deadline` |
| `litigation-confirm-deadline` | Human confirms a provisional deadline | `confirm_deadline` |
| `litigation-search-archive` | Arabic full-text search over `document_chunks` (semantic half inactive) | `run_research` |
| `litigation-chronology` | On-demand case timeline from transcribed pages (Gemini, not persisted) | `run_research` |
| `litigation-manage-template` | Create/deactivate drafting templates | `manage_templates` |
| `litigation-draft` | Generates a first draft (Gemini), **zero real citations by design** — see below | `generate_draft` |
| `litigation-export-draft` | Renders draft to real `.docx` (via `npm:docx`, RTL/bidi-correct — NOT PDF, jsPDF breaks Arabic shaping) | `export_matter` |
| `litigation-manage-authority` | Manual entry into the legal corpus | `manage_corpus` |
| `litigation-research` | Grounded research — answers ONLY from retrieved `authority_chunks`, refuses (doesn't call the model at all) when nothing retrieves | `run_research` |
| `sign-document-url` | The only path to any file in Storage — signs URLs for `matter-documents`/`matter-drafts`/`authorities` | (matter access or `authorities` bucket rule) |

Also relevant, pre-existing: `sign-document-url` is shared with the back-office portal too.

## Frontend — `litigation-agent.html`

Single file, ~1400 lines, vanilla JS (no framework, no build step), RTL, hash-based router (`#/matters`, `#/matters/:id`, `#/review`, `#/search`, `#/templates`, `#/corpus`, `#/research`). Uses a small `h()` DOM-builder helper — **do not switch to `innerHTML` string templates for anything containing user data** (matter labels, party names, etc.) — the current code avoids XSS by building real DOM nodes; string-templating user content back in would reopen that.

**Design system**: restyled mid-session to match a design template the user supplied (uploaded zip `hoyaam--main`, a React/Vite mockup — not itself part of this app, just the visual reference). Adopted verbatim: CSS custom properties (`--bg`, `--card`, `--text`, `--text-2`, `--accent`, `--good`/`--warn`/`--danger`, `--gold`), fonts (IBM Plex Sans Arabic / Amiri / IBM Plex Mono via Google Fonts), dark mode via `[data-theme]`, component class vocabulary (`.sidebar`, `.dense-table`, `.chip-confirmed`/`.chip-provisional`, `.split-review-container`, `.file-stepper`, `.modal-card`, etc.). **If you add a new screen, match this vocabulary — don't invent new component classes.**

**⚠️ Known near-miss, don't repeat it:** mid-session, a "fix the design" pass was done by rewriting the whole file from a stale mental model and it silently deleted already-shipped functionality (drafting, export, chronology, search, templates, corpus, research screens) that only existed in the live file, not in anything re-derivable from conversation memory. It was caught by diffing against `git log` / the previous commit before the user noticed. **Before doing any large rewrite of `litigation-agent.html`, diff your intended output against the current file on disk (or the latest commit) — don't reconstruct it from memory/context.**

### Screens implemented
- Auth: login, MFA (TOTP) challenge, "no factor enrolled" redirect message (enrollment itself only exists in the separate back-office portal)
- Matters: list (dense table), detail (7 tabs: overview / parties / hearings / deadlines / documents / drafts / chronology), new-matter dialog
- Review queue: split view (live document preview via signed URL + editable extraction cards with confidence chips)
- Upload: modal with a real 3-step stepper (upload → Gemini extract → done/failed)
- Hearings: add-hearing dialog
- Deadlines: propose dialog (only shown if ≥1 active `deadline_rules` row exists), confirm button
- Archive search, Templates (list + create), Legal corpus (list + add-authority, with an explicit on-screen "HONEST GAP" notice), Grounded research

### Known workflow gotcha (tell the user again if they hit it)
Creating a matter does **not** grant the creator `matter_access` — that's always a separate, deliberate grant (via the back-office portal's Access → Matter access screen), even for the `owner` role. A newly created matter is invisible in the list until someone grants access to it.

## Deliberately NOT done — read before "fixing" these

- **`authorities`/`authority_chunks` (the legal corpus) is empty on purpose.** The user asked at one point to "feed it with all Egyptian law and Islamic Sharia" — **this was refused**, explicitly and at length, because generating/approximating real legal text from model memory into the one table this system treats as ground truth for citations is exactly the fabrication failure mode the whole architecture exists to prevent. If asked again: same answer. Real sourcing requires an actual document (official gazette text, a licensed legal database export, etc.) fed through a faithful *extraction* pipeline (like `legal-extract`, not generation) — that pipeline does not exist yet and would need to be built once real source material is available. See the conversation for a list of real Egyptian sources found via web search (official gazette portal, `cc.gov.eg` for Cassation principles, `dar-alifta.org` for official fatwas) if the user wants pointers again.
- **Embeddings / semantic search is wired but inactive.** `document_chunks.embedding` and `authority_chunks.embedding` columns exist with `hnsw` indexes ready, but nothing populates them — no embeddings-provider key is configured. Arabic full-text search (`websearch_to_tsquery`) works today and is what `litigation-search-archive`/`litigation-research` actually use.
- **`litigation-draft` never cites real authorities** — the corpus is empty, so the model is explicitly instructed to emit the literal placeholder `[يحتاج استشهاد قانوني]` instead of any citation, real or invented. Don't "improve" this by having it cite anything until a real corpus exists.
- **`deadline_rules` table is empty on purpose** — Phase 0 of the original build plan requires a lawyer to author and sign real deadline rules (with citations) before any deadline can be computed for real. `fn_compute_deadline` enforces this at the DB level (refuses non-`active` rules).
- No OCR-of-adjournment-note pipeline (hearings are manual entry only), no TOTP enrollment UI in this app (use the back-office portal), no bulk template/authority import (one at a time, by design, to keep a human reviewing every real legal source that goes in).

## Outstanding: GitHub push access

`git push` to this repo fails with a 403 — the Claude GitHub App isn't installed/authorized for the `01015523142az-hash` org from this session. **7 commits sit locally, unpushed**, on `claude/egypt-lawyer-ai-agent-u4h1qb`. This zip is the workaround: continue from the zip's contents in the new session/account rather than expecting `git pull` to have this history — unless the new session's GitHub access is different, in which case push these commits for real. Git history (`.git/`) is included in this zip specifically so the commit log and messages aren't lost.

## Quick orientation for whoever picks this up

1. Read the plan artifact ("Litigation Agent Build Plan") for the product intent, phases, and hard-problem design decisions — everything above assumes familiarity with it.
2. Everything backend-side is already live on Supabase project `xjxzkjyotbumxtddkepd` — no migration/deploy step needed to keep using it as-is.
3. `litigation-agent.html` is the only file to actually edit for frontend changes; upload it manually wherever the user is hosting it (same pattern used throughout this session — GitHub push access was blocked the whole time).
4. Set `GEMINI_API_KEY` (if not already set) before testing anything that calls the model.
5. Don't populate the legal corpus with generated text. Ever. Ask for real source material.
