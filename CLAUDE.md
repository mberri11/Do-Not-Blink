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

## AD SDK NOTE
`play-services-ads` ships a `MobileAdsInitProvider` that throws at process start when
`com.google.android.gms.ads.APPLICATION_ID` meta-data is absent from the manifest. The manifest
currently carries **Google's public test App ID**
(`ca-app-pub-3940256099942544~3347511713`). Replace it with the real AdMob App ID before any
release build. Test ad unit IDs must likewise never ship.
