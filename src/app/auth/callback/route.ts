import { type EmailOtpType } from '@supabase/supabase-js';
import { NextResponse, type NextRequest } from 'next/server';
import { createClient } from '@/lib/supabase/server';

// Lands here from the Supabase invite email. The exact query shape
// depends on the email template (code= for the PKCE flow, or
// token_hash=/type= for the OTP-verification flow) — handle both rather
// than assuming, since nothing in this session can inspect the live
// project's email template config (Dashboard-only, see the handover
// README's "manual dashboard steps").
export async function GET(request: NextRequest) {
  const { searchParams, origin } = new URL(request.url);
  const code = searchParams.get('code');
  const tokenHash = searchParams.get('token_hash');
  const type = searchParams.get('type') as EmailOtpType | null;

  const supabase = await createClient();

  if (code) {
    const { error } = await supabase.auth.exchangeCodeForSession(code);
    if (!error) return NextResponse.redirect(`${origin}/invite/accept`);
  } else if (tokenHash && type) {
    const { error } = await supabase.auth.verifyOtp({ token_hash: tokenHash, type });
    if (!error) return NextResponse.redirect(`${origin}/invite/accept`);
  }

  return NextResponse.redirect(`${origin}/login?error=invite-link-invalid`);
}
