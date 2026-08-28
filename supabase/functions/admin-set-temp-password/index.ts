// admin-set-temp-password
//
// POST { user_id, reason? } -> { temp_password, email }
//
// Real replacement for the ad hoc "run raw SQL against auth.users"
// pattern used earlier in this build's life (see CHANGELOG — Adham
// Hassan, Ghadi Youssef both got a manual reset that way) — an actual
// capability-gated, audit-logged path instead of an operator hand-running
// SQL against the project.
//
// Deliberately restricted to the owner role specifically — narrower than
// the manage_users capability itself, which admin also holds. Setting
// someone else's password directly is a materially more sensitive action
// than the rest of what manage_users covers (inviting, role changes,
// offboarding), and the firm asked for it to stay owner-only. This is a
// hand-written role check below, not requireCapability('manage_users',
// ...) — requireCapability's generic "missing capability" message would
// be actively misleading for an admin who genuinely does hold
// manage_users but is still correctly denied here.
//
// Sets a securely-generated random password directly via the Admin API
// (auth.admin.updateUserById), together with
// user_metadata.must_change_password = true, so the account is forced
// onto the app's own "set new password" screen (renderSetNewPassword in
// litigation-agent.html) before it can be used for anything else,
// including MFA enrollment — see that screen and boot()'s own comments
// for why the check happens before MFA, not after.
//
// The generated password is returned ONCE, in this response only, for
// the caller to relay to the account owner through a separate channel.
// It is never persisted in plaintext anywhere and never written to
// audit_log — only the fact that a reset happened is logged, same
// discipline as every other sensitive action in this build.
//
// Does not send an email. Supabase Auth's own mailer can't embed an
// arbitrary generated value like this into its fixed templates (its
// recovery/invite templates only support Supabase's own template
// variables) — building a real "email the password" path needs a
// separate email-sending provider (Resend/SendGrid/SES/SMTP) configured
// with its own credentials, which is a decision + setup step for the
// firm, not something this function can do unilaterally. The self-service
// reset flow (renderForgotPassword, using resetPasswordForEmail) is the
// one path in this app that does send a real email, and does so via
// Supabase's own supported mechanism.

import 'jsr:@supabase/functions-js/edge-runtime.d.ts';
import { requireCaller, logAction, serviceClient, json, badRequest, corsPreflight, AdminError } from './_shared/admin.ts';

// Avoids visually-ambiguous characters (0/O, 1/l/I) — this is meant to be
// read aloud or retyped by a human relaying it out-of-band, not just
// copy-pasted.
const PASSWORD_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#%^&*';

function generateTempPassword(length = 16): string {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => PASSWORD_ALPHABET[b % PASSWORD_ALPHABET.length]).join('');
}

Deno.serve(async (req: Request) => {
  const preflight = corsPreflight(req);
  if (preflight) return preflight;

  if (req.method !== 'POST') return badRequest('POST only');

  const caller = await requireCaller(req);
  if (caller instanceof Response) return caller;
  const { callerId } = caller;
  const asService = serviceClient();

  const { data: ownerRow, error: roleError } = await asService
    .from('user_roles')
    .select('id')
    .eq('user_id', callerId)
    .eq('role_id', 'owner')
    .is('revoked_at', null)
    .maybeSingle();
  if (roleError) return json({ error: roleError.message }, 500);
  if (!ownerRow) {
    await logAction(asService, callerId, 'temp_password_set', {
      success: false,
      reason: 'denied: caller is not the firm owner',
    });
    return new AdminError(403, 'this action is restricted to the firm owner');
  }

  const body = await req.json().catch(() => null);
  const { user_id, reason } = body ?? {};
  if (!user_id) return badRequest('user_id is required');

  const { data: targetUser, error: targetError } = await asService.auth.admin.getUserById(user_id);
  if (targetError || !targetUser?.user) return badRequest('no such user');

  const tempPassword = generateTempPassword();

  const { error: updateError } = await asService.auth.admin.updateUserById(user_id, {
    password: tempPassword,
    user_metadata: { ...(targetUser.user.user_metadata || {}), must_change_password: true },
  });
  if (updateError) return json({ error: updateError.message }, 409);

  await logAction(asService, callerId, 'temp_password_set', {
    targetTable: 'auth.users',
    targetId: user_id,
    after: { email: targetUser.user.email, must_change_password: true },
    reason: reason || null,
  });

  return json({ temp_password: tempPassword, email: targetUser.user.email });
});
