import { createServerClient } from '@supabase/ssr';
import { cookies } from 'next/headers';

// Server Component / Route Handler / Server Action client. Reads/writes the
// session cookie Supabase manages — see src/proxy.ts for the piece that
// keeps it refreshed on every request.
export async function createClient() {
  const cookieStore = await cookies();

  return createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return cookieStore.getAll();
        },
        setAll(cookiesToSet) {
          try {
            cookiesToSet.forEach(({ name, value, options }) =>
              cookieStore.set(name, value, options),
            );
          } catch {
            // Called from a Server Component that can't set cookies (no
            // response to attach them to) — proxy.ts refreshes the
            // session on every request, so this is safe to ignore.
          }
        },
      },
    },
  );
}
