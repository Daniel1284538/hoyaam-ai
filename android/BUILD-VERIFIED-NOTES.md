# Hoyaam AI Android — build-verified update

This closes the gap the previous `FIXES-APPLIED.md` pass flagged explicitly:
"there is still no way to verify any of this compiles without an actual
Android build." This zip has now actually been compiled — a real Android
SDK + Gradle toolchain was set up from scratch and `assembleDebug` ran to
completion — not a source-level review this time. The debug APK from this
exact build was sent alongside this zip.

## New since the last delivery

1. **Three previously-missing features, now with real UI** (backend/DTOs/
   ViewModel wiring existed as source only before; this adds the screens):
   - **Hearing briefing** (`litigation-hearing-briefing`) — a button on the
     Drafts tab, "توليد إحاطة تحضيرية للجلسة القادمة".
   - **Case / document summarization** (`litigation-summarize`) — a button
     on the Overview tab for a case-wide summary, plus a per-document
     summarize action on the Documents tab; both open a shared result
     dialog (summary text, key points, flags, or the server's own "nothing
     to summarize" note).
   - **Conflict-of-interest check** (`litigation-check-conflicts`) — a
     "فحص تعارض المصالح" button on the Parties tab, pre-filled with the
     current matter's party names (editable), showing fuzzy-matched
     conflicts against the rest of the firm's other matters.

2. **Real mascot image** — the actual "هويام" illustration (the same SVG
   the web app uses as `AVATAR_SRC`) now appears on the login screen,
   replacing the placeholder circular badge with the letter "هـ". Sourced
   by decoding the web app's own embedded SVG and rasterizing it (not
   redrawn/approximated) into the standard Android density buckets
   (mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi).

## What made the build actually fail, and the real fixes

None of this was guessed — every one of these was reproduced, diagnosed
from a real Gradle log or stacktrace, and confirmed fixed by a build that
got further than the previous failure:

- **`gradle/libs.versions.toml`**: `googleDevtoolsKsp` was pinned to
  `"2.3.5"`. That version (and, it turned out, the latest patch in the
  same line, `2.3.11`, tried next) has a genuine intermittent bug in this
  AGP 9.1.1 "built-in Kotlin" configuration — `kspDebugKotlin`
  unpredictably threw a real NPE (`Cannot invoke "List.get(int)" because
  "path" is null`) inside KSP's own `KspGradleConfig`. It surfaced
  differently across attempts (sometimes as a configuration-cache
  serialization failure, sometimes as the task failing outright), and is
  flaky rather than deterministic — a bare retry of the identical build
  succeeded. Bumped the pin to `2.3.11` (the current released version in
  KSP's decoupled-versioning line this AGP setup needs — do not downgrade
  to a Kotlin-version-prefixed release like `2.2.10-2.0.2`, that line
  predates AGP's built-in-Kotlin support and fails differently, with
  `android.sourceSets` vs `kotlin.sourceSets` errors). If `kspDebugKotlin`
  fails with this exact NPE again, retrying the same task is the known
  workaround — it is not a real code or config problem when it happens.
- **`gradle.properties`**: `org.gradle.configuration-cache=true` disabled.
  Same underlying KSP bug also broke Gradle's configuration-cache
  serialization step specifically; turning that off stopped it from
  aborting otherwise-successful builds.
- **`debug.keystore`**: didn't exist. The project's own `app/build.gradle.kts`
  defines a custom `debugConfig` signing config pointing at
  `${rootDir}/debug.keystore` (not Android's default
  `~/.android/debug.keystore`), with the standard debug alias/password
  (`androiddebugkey` / `android`). **This zip does not include a keystore
  file** (keystores aren't meaningful to version-control/re-import) — run
  once before building:
  ```
  keytool -genkeypair -v -keystore debug.keystore -storepass android \
    -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
  ```

## Still real gaps, unchanged from the last delivery

- Token storage is still plain `SharedPreferences`, not
  `EncryptedSharedPreferences`.
- Calendar sync, local Room notes/tags, and `PdfExporter` were not
  re-audited in this pass either.
- This build environment has no device/emulator, so the APK has been
  verified to be a real, valid, correctly-signed package (manifest, dex
  classes, resources all present) — not run on a device. Install and smoke
  test is the next real step.
