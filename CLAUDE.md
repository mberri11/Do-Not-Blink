# DO NOT BLINK — project brief

Android game. Read this whole file before writing anything.

## STACK — non-negotiable
- Kotlin, Jetpack Compose, native. No Flutter, no React Native, no Expo, no game engine.
- Single module `:app`. No multi-module split. No shared library module — this is app #1 of a
  studio and the shared template will be extracted LATER, from app #2. Do not create one.
- minSdk 26, targetSdk 36, compileSdk 36. Google Play requires target API 36 for new
  submissions from 31 Aug 2026 and this app submits after that date.
- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`). Compose BOM.
- Kotlin 2.x: the Compose compiler is the `org.jetbrains.kotlin.plugin.compose` Gradle plugin.
  Do NOT set `composeOptions.kotlinCompilerExtensionVersion` — that is the pre-Kotlin-2.0 way
  and it will fail.
- Fully offline game logic. The only network traffic in the entire app is the Google Mobile Ads
  SDK. No other network code exists anywhere.
- No IAP. No analytics SDK. No Firebase. No Crashlytics.
- Persistence: DataStore Preferences only. No Room in this app — there is nothing relational.

## DEPENDENCIES — exactly these, nothing else
  androidx.core:core-ktx
  androidx.core:core-splashscreen
  androidx.activity:activity-compose
  androidx.lifecycle:lifecycle-runtime-compose
  androidx.lifecycle:lifecycle-viewmodel-compose
  androidx.compose:compose-bom  (+ ui, ui-graphics, foundation, material3, ui-tooling-preview)
  androidx.datastore:datastore-preferences
  com.google.android.gms:play-services-ads
  com.google.android.ump:user-messaging-platform
  test: junit, kotlinx-coroutines-test, androidx.compose.ui:ui-test-junit4, ui-test-manifest

## VERIFICATION RULES — these override any instinct you have
- You have no eyes. You cannot see rendered output. You may NEVER write "verified",
  "matches the design", "pixel-perfect", "looks correct", "visually confirmed", or any
  equivalent. Visual state is decided by a human on a physical device, and only there.
- At the end of every stage, print: the exact commands you ran verbatim, their exit codes as
  integers, tests run/passed/failed as integers, and any produced APK path with its byte size.
  Print results. Never describe them.
- When a stage ends with STOP, you stop. You do not begin the next stage, you do not "prepare"
  for it, you do not scaffold ahead.

## PROJECT FACTS
- Package / applicationId: `com.simobr.donotblink` (studio: Simobr Studio, matching the existing
  `com.simobr.aurafy` convention in Google Play Console).
- App name: "Do Not Blink".
- Portrait-first but NOT orientation-locked. API 36 ignores orientation locks on sw600dp
  displays, so `screenOrientation` in the manifest is pointless and misleading — it is omitted.
- `design/` holds the mockups. Never treat a mockup as verification of rendered output.

## PINNED TOOLCHAIN (Stage 0)
Recorded so later stages do not drift. Change only with a reason.

| Thing | Version |
| --- | --- |
| Gradle wrapper | 8.14.4 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / compose-compiler plugin | 2.4.10 |
| Compose BOM | 2026.06.01 (Compose 1.11.4 — the newest line that still builds at compileSdk 36; Compose 1.12 demands compileSdk 37 + AGP 9.1) |
| JDK (local) | 17 (only JDK on this machine) |
| Java/Kotlin bytecode target | 17 |

All versions live in `gradle/libs.versions.toml`. Never hardcode a version in a
`build.gradle.kts`.

## PINNED GAME CONSTANTS
Architect-approved values. Changing one is a conversation, not an edit — `DifficultyTest` pins
them and will name the offender.

| Constant | Value | Approved |
| --- | --- | --- |
| `Difficulty.BAND_BASE_DP` | 7.5 (was 9.5) | Stage 5.5 — 2026-08-22 |
| `Difficulty.OVERSHOOT_MARGIN_DP` | 1.5 (was 3.0) | Stage 5.5 — 2026-08-22 |
| `Difficulty.HUMAN_FLOOR_MS` | 52 | Stage 2 |
| `Difficulty.BAND_MIN_DP` | 3.5 | Stage 2 |

The two Stage 5.5 changes firm up the EARLY game only: the streak-0 half-window drops from
~205ms to ~162ms and the fairness clamp now engages at streak 10 instead of later. The endgame
plateau is untouched — every clamped streak is still judged on a 52ms half-window.

### Daily Trial + Reflex Test (v1.2.0 — 2026-09-07)

| Constant | Value | Why |
| --- | --- | --- |
| `DailyTrial.RINGS` | 10 | One share grid, one screen, one comparable result |
| `DailyTrial.streakForRing(i)` | `i * 3` | Ramps ring 1..10 across streaks 0..27: a 162ms half-window down to the 52ms plateau, crossing `START_JITTER_FROM_STREAK` at ring 8 and never reaching `BLACKOUT_FROM_STREAK` |
| `GameViewModel.DAILY_MISS_DWELL_MS` | 700 | A missed trial ring is a beat, not an ending |
| `Twitch.MIN_WAIT_MS` / `MAX_WAIT_MS` | 1400 / 4600 | Uniform: any distribution with a mode is something to anticipate |
| `Twitch.GUESS_FLOOR_MS` | 100 | Below human visual reaction — a guess, not a reading |
| `Twitch.ABANDONED_CEILING_MS` | 2000 | Above it the player was not looking. The stored best is a MINIMUM, so one absurd lone reading would stand as the record |
| `Twitch.ATTEMPTS` | 5 | Enough for a best to mean something, short enough to repeat |
| `Twitch.chirpTier` | 0 / 1 / 2 at 220ms, 340ms | Reward-chirp pitch. Boundaries are GRADE boundaries — defined here, not in the audio code, so the sound and the word on screen cannot drift apart |

Rules that are design, not implementation detail, and must not be "optimised" away:
- A missed trial ring does **not** end the trial. All ten are always played.
- The trial offers **no rewarded continue** — a second chance would make one player's ten rings a
  different contest from everyone else's, which is the one thing a shared daily seed cannot survive.
- A trial never touches the endless best streak, the title ladder, or the interstitial cadence.
- Walking out of a trial part-way forfeits the remaining rings and spends the day.
- **The reflex test's signal makes no sound and no vibration.** A cue at that instant is something
  to react to other than the ring, and auditory reaction is ~40ms faster than visual — the reading
  would stop measuring what it claims to. Feedback fires on the TAP only.

## AD POLICY INVARIANTS (v1.2.0 — 2026-09-07)

The numbers in `ads/AdPolicy.kt` are tunable and were loosened once already. These are not:

- **Never an interstitial straight after a personal best**, and **never straight after a rewarded
  continue.** These two exclusions are what a Disruptive Ads strike is actually about. Tune the
  counts, never delete these.
- **The Daily Trial shows no interstitial at all**, and does not feed the interstitial cadence.
  It is the retention feature; it stays clean.
- **A non-run activity must use `AdSession.onSideActivityEnded()`, never `onRunEnded(false, false)`.**
  The latter clears the two protections above — finishing a reflex sitting would silently cancel the
  protection a personal best had just earned.
- The banner never appears during play: fail screen and SETTINGS only, and always in a slot
  reserved at its true height so nothing moves depending on fill.
- One rewarded surface per purpose, and a failed or dismissed ad **always** leaves the player
  exactly where a world with no ads would have left them.

## PRIVACY POLICY
`donotblink.privacyPolicyUrl` in `gradle.properties` -> `BuildConfig.PRIVACY_POLICY_URL` ->
the SETTINGS row. Public information, committed on purpose. The same URL must appear in the Play
Console listing and in AdMob's app settings.

## AD SDK NOTE
`play-services-ads` ships a `MobileAdsInitProvider` that throws at process start when
`com.google.android.gms.ads.APPLICATION_ID` meta-data is absent from the manifest. The manifest
currently carries **Google's public test App ID**
(`ca-app-pub-3940256099942544~3347511713`). Replace it with the real AdMob App ID before any
release build. Test ad unit IDs must likewise never ship.
