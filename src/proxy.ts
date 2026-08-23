import { createServerClient } from '@supabase/ssr';
import { NextResponse, type NextRequest } from 'next/server';

// Next.js 16 renamed middleware.ts -> proxy.ts (same runtime behavior,
// see node_modules/next/dist/docs/01-app/01-getting-started/16-proxy.md).
// This is the session-refresh + route-guard layer: it runs before every
// request, keeps the Supabase session cookie fresh, and redirects to the
// right step of the auth flow (login / MFA challenge / MFA enrollment)
// before a request ever reaches a page. Every route guarded here is an
// "optimistic" check per Next's own auth guide — pages and Server Actions
// still call requireCapability()-backed Edge Functions or RLS-scoped
// queries for the real enforcement, same split the backend already uses.

const PUBLIC_ROUTES = ['/login'];

// Reachable by an authenticated user regardless of where they stand on
// MFA — the invite-accept flow sets a password before MFA enrollment even
// starts, so it can't be gated by the same aal-based redirect as
// everything else without creating a redirect loop.
const ONBOARDING_EXEMPT_ROUTES = ['/invite/accept'];

export async function proxy(request: NextRequest) {
  let response = NextResponse.next({ request });

  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return request.cookies.getAll();
        },
        setAll(cookiesToSet) {
          cookiesToSet.forEach(({ name, value }) => request.cookies.set(name, value));
          response = NextResponse.next({ request });
          cookiesToSet.forEach(({ name, value, options }) =>
            response.cookies.set(name, value, options),
          );
        },
      },
    },
  );

  const { data: { user } } = await supabase.auth.getUser();
  const path = request.nextUrl.pathname;

  if (path.startsWith('/auth/callback')) {
    return response;
  }

  if (!user) {
    if (!PUBLIC_ROUTES.includes(path)) {
      return NextResponse.redirect(new URL('/login', request.url));
    }
    return response;
  }

  if (ONBOARDING_EXEMPT_ROUTES.includes(path)) {
    return response;
  }

  // Authenticated. Figure out where they stand on MFA before letting them
  // any further in — mfa_verified() in the database requires aal2 on
  // every privileged table, so a session that hasn't reached aal2 can't
  // actually do anything yet regardless of what the UI shows.
  const { data: aal } = await supabase.auth.mfa.getAuthenticatorAssuranceLevel();
  const hasFactor = aal?.nextLevel === 'aal2';
  const isVerified = aal?.currentLevel === 'aal2';

  if (path === '/login') {
    if (isVerified) return NextResponse.redirect(new URL('/', request.url));
    if (hasFactor) return NextResponse.redirect(new URL('/mfa-challenge', request.url));
    return NextResponse.redirect(new URL('/mfa-enroll', request.url));
  }

  if (isVerified) {
    return response;
  }

  if (hasFactor) {
    if (path !== '/mfa-challenge') {
      return NextResponse.redirect(new URL('/mfa-challenge', request.url));
    }
    return response;
  }

  if (path !== '/mfa-enroll') {
    return NextResponse.redirect(new URL('/mfa-enroll', request.url));
  }
  return response;
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)'],
};
