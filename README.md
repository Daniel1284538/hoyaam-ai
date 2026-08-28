# Hoyaam AI (هويام المحامية)

Litigation-management platform for the Egyptian legal market, backed by Supabase.

## Structure

- `android/` — native Android client (Kotlin, Jetpack Compose). See
  `BUILD-VERIFIED-NOTES.md` for build setup, including the debug keystore and
  a known KSP/AGP flake and its workaround.
- `supabase/` — backend: SQL migrations (`supabase/migrations/`) and Edge
  Functions (`supabase/functions/`).
- `litigation-agent.html` — standalone web client.
- `CHANGELOG.md` — project history.
