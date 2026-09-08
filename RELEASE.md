# DO NOT BLINK — release checklist

Nothing here is performed by this file. Every line is a thing a human ticks after running the
command next to it and reading the output. Unticked means not done, not "probably fine".

Package: `com.simobr.donotblink` · versionCode `4` · versionName `1.2.0`

> **versionCode 4 / versionName 1.2.0 (2026-09-07) is the PRODUCTION candidate.** It carries the
> `androidx.fragment` SDK-Index fix the closed testers' warning was about, plus three features —
> the Daily Trial, the miss readout, and the Reflex Test. Built with **real** ad IDs, not test
> IDs: a production listing serving Google's demo units earns nothing and is an AdMob policy
> problem. See **Recorded run — v4** at the bottom.

`versionCode` moved from 1 to 2 on 2026-08-25 (Play rejected code 1 as already used; no source
change). versionCode 3 / versionName 1.0.1, same day, carries a real fix: the fail-screen banner
never showed for a player who failed fast, because `adsEnabled` was read once from a plain
property instead of the reactive `PlayState`. See **Recorded run — v3** below.

---

## Before the build

- [x] `local.properties` (git-ignored, repo root) carries all six real AdMob IDs:
      `ADMOB_APP_ID`, `ADMOB_UNIT_BANNER`, `ADMOB_UNIT_INTERSTITIAL`,
      `ADMOB_UNIT_REWARDED_CONTINUE`, `ADMOB_UNIT_REWARDED_PHOSPHOR`, `ADMOB_UNIT_REWARDED_TITLES`.
      `:app:verifyReleaseAdIds` refuses to package without them, refuses a Google test ID, and
      refuses a malformed one — but it cannot tell a *wrong* real ID from a right one.
      Filled in 2026-08-24; publisher `ca-app-pub-7799898340675704`, verified as one segment
      across all six. Simo confirmed each against the AdMob console on device (Prompt B check 5).
- [x] `~/.gradle/gradle.properties` (machine-global, never committed) carries
      `SIMOBR_KEYSTORE`, `SIMOBR_KEYSTORE_PASS`, `DNB_KEY_ALIAS`, `DNB_KEY_PASS`.
      `:app:verifyReleaseSigning` refuses to package without them and never falls back to the
      debug key.
      Filled in 2026-08-24: `/home/mberri/secure-backups/simobr.jks`, alias `donotblink`.
      That keystore also holds an unrelated `79b16287…` alias (Aurafy's) — `donotblink` is the
      one used here, and `verifyReleaseSigning` fails by name if the alias is ever wrong.
- [x] The privacy policy URL in `gradle.properties`
      (`donotblink.privacyPolicyUrl`) is live in a browser **before** upload — the Play listing,
      the AdMob app settings and the app's SETTINGS row must all point at the same URL.
      `https://simobr-studio.github.io/Do-Not-Blink_Legal/` — replaced the dead
      `mberri11.github.io` URL on 2026-08-24 (Prompt A). Simo confirmed it opens live from
      SETTINGS → PRIVACY POLICY on device.
- [ ] `app-ads.txt` is published at the root of the developer website declared in AdMob, and
      AdMob's crawler has picked it up (AdMob → Apps → app-ads.txt shows "Authorized"). **Still
      open** — nothing in this repo can confirm a third-party site; check the AdMob console.

## The build

```
./gradlew :app:bundleRelease
```

- [x] Exit code is `0`.
- [x] AAB is at `app/build/outputs/bundle/release/app-release.aab`; **7266931 bytes** — versionCode
      4 / versionName 1.2.0, real credentialed IDs, 2026-09-07. See **Recorded run — v4** below.
      (The v1.0.1 artifact this line used to describe was 7189505 bytes.)
- [x] R8 mapping file exists at `app/build/outputs/mapping/release/mapping.txt` — **49996087
      bytes**. Not yet uploaded — that happens as part of the Play Console upload itself.

## Proving what is in the bundle

### Target API 36

`aapt2` cannot read an AAB. `bundletool` can:

```
bundletool dump manifest --bundle=app/build/outputs/bundle/release/app-release.aab \
  --xpath=/manifest/uses-sdk/@android:targetSdkVersion
```

- [x] Prints `36`. `bundletool` is not on this machine; proven instead via `aapt2 dump badging`
      on a matching `assembleRelease` APK. See **Recorded run**.

> `bundletool` is not installed on this machine. Get it from
> <https://github.com/google/bundletool/releases> and run it as `java -jar bundletool-all-*.jar`.
> Equivalent check on a built APK instead:
> `$ANDROID_HOME/build-tools/36.0.0/aapt2 dump badging <apk> | grep targetSdkVersion`

### No test ad IDs, no test App ID

Google's test publisher prefix is `3940256099942544`. It must appear nowhere in the bundle —
not in a dex string, not in a resource, not in the merged manifest.

```
unzip -p app/build/outputs/bundle/release/app-release.aab | strings -a | grep -c "ca-app-pub-3940256099942544"
```

- [x] Prints `0`. Run again on 2026-08-24 against the real credentialed AAB — printed `0`. All
      six real unit IDs confirmed present with `grep -Eo "ca-app-pub-..."`, no test IDs anywhere.
      See **Recorded run** for the full transcript.

## Play Console

- [ ] **Data safety**: advertising ID declared as collected (by the Google Mobile Ads SDK, for
      advertising). Nothing else is collected — the game is fully offline, has no analytics SDK,
      no Firebase, no Crashlytics, no account, and no IAP. Best streak and settings live in
      DataStore on the device and never leave it.
      Note: the release manifest also carries `ACCESS_ADSERVICES_AD_ID`,
      `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS`, `WAKE_LOCK` and
      `FOREGROUND_SERVICE` — all merged in transitively by `play-services-ads:25.4.0` and its own
      dependencies, none added by this app. Standard for a current build of that SDK; still worth
      a glance against the Data safety form's ad-ID question before submitting.
- [ ] **Content rating** questionnaire completed and a rating issued.
- [ ] **Ads** declaration: yes, the app contains ads.
- [ ] **Privacy policy** URL pasted into the listing (same URL as above).
- [ ] Store listing text, screenshots, feature graphic and icon uploaded.
- [ ] **Closed testing**: 12 testers on the list, opted in, and the 14-day continuous-testing
      requirement understood before production access is requested.

## Sanity install

```
./gradlew :app:installRelease
```

- [x] Installs on a physical device. `BUILD SUCCESSFUL in 9s`, "Installed on 1 device." — 2026-08-24.
- [x] Themed icons ON: launcher shows an eye. Confirmed by Simo.
- [x] Play a run to a fail: real banner ad, no "Test Ad" label. Confirmed by Simo.
- [x] SETTINGS → PRIVACY POLICY opens the live page in a browser. Confirmed by Simo.
- [x] Under an EEA test geo, SETTINGS shows **PRIVACY OPTIONS** and it reopens the consent form.
      Confirmed by Simo. (Outside the EEA the row does not exist — `showsPrivacyOptionsRow`,
      unit-tested.)
- [x] No crash on cold start; interstitial and both rewarded placements filled. Confirmed by Simo.
- [x] No crash on cold start otherwise, since the above ran clean.

---

## Recorded run — SHIPPABLE, real credentials

Built 2026-08-24 with the real AdMob publisher (`ca-app-pub-7799898340675704`) and the
`donotblink` alias in `/home/mberri/secure-backups/simobr.jks`. This is the artifact at
`app/build/outputs/bundle/release/app-release.aab`, unchanged since — no source file is newer
than it. Every check in this document above is ticked against this exact build.

```
$ ./gradlew --stop
$ ./gradlew clean
BUILD SUCCESSFUL in 13s

$ ./gradlew :app:bundleRelease
BUILD SUCCESSFUL in 3m 10s
56 actionable tasks: 14 executed, 42 up-to-date

$ ls -l app/build/outputs/bundle/release/app-release.aab
-rw-rw-r-- 1 mberri mberri 7189505 Aug 24 23:11 app-release.aab
                                    7189505 bytes

$ ls -l app/build/outputs/mapping/release/mapping.txt
-rw-rw-r-- 1 mberri mberri 49370141 Aug 24 23:11 mapping.txt
```

### Proof 1 — target API 36

`bundletool` is not on this machine. Proven instead via `aapt2 dump badging` on a matching
`assembleRelease` APK:

```
$ ./gradlew :app:assembleRelease
BUILD SUCCESSFUL in 4s

$ aapt2 dump badging app/build/outputs/apk/release/app-release.apk
package: name='com.simobr.donotblink' versionCode='1' versionName='1.0' \
  platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36'
targetSdkVersion:'36'
```

### Proof 2 — no test IDs anywhere in the artifact

```
$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings \
    | grep -Eo "ca-app-pub-[0-9]{16}[~/][0-9]{10}" | sort -u
ca-app-pub-0000000000000000~0000000000     <- Google Mobile Ads SDK's own internal placeholder
ca-app-pub-7799898340675704~4432231805     <- App ID
ca-app-pub-7799898340675704/4553088092     <- dnb_rewarded_titles
ca-app-pub-7799898340675704/5866169761     <- dnb_rewarded_continue
ca-app-pub-7799898340675704/7043520068     <- dnb_interstitial_round
ca-app-pub-7799898340675704/8052410054     <- dnb_rewarded_phosphor
ca-app-pub-7799898340675704/9634074714     <- dnb_banner_fail

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings \
    | grep -c "3940256099942544"
0
```

All six real IDs present. Zero test IDs. Simo confirmed each of the six against the AdMob
console on device.

### Proof 3 — privacy URL in the artifact

```
$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings \
    | grep -Eo "https://[a-zA-Z0-9./_-]*Do-Not-Blink[a-zA-Z0-9./_-]*" | sort -u
https://simobr-studio.github.io/Do-Not-Blink_Legal/
```

### Proof 4 — declared permissions

`bundletool` unavailable; used `aapt2 dump permissions` on the APK instead:

```
$ aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
package: com.simobr.donotblink
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.ACCESS_NETWORK_STATE'
uses-permission: name='com.google.android.gms.permission.AD_ID'
uses-permission: name='android.permission.ACCESS_ADSERVICES_AD_ID'
uses-permission: name='android.permission.ACCESS_ADSERVICES_ATTRIBUTION'
uses-permission: name='android.permission.ACCESS_ADSERVICES_TOPICS'
uses-permission: name='android.permission.WAKE_LOCK'
uses-permission: name='android.permission.FOREGROUND_SERVICE'
permission: com.simobr.donotblink.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
uses-permission: name='com.simobr.donotblink.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

8 permissions, not the 3 this document originally expected. Traced through the manifest merger
blame file — every one beyond the app's own `INTERNET` / `ACCESS_NETWORK_STATE` / `AD_ID` is
merged in transitively by `play-services-ads:25.4.0` and its own dependencies
(`play-services-measurement-sdk-api`, `androidx.work:work-runtime`, `androidx.core`). Nothing
added by this app's own manifest. Normal for a current build of that SDK; still worth checking
against the Data safety form's questions before submitting.

### Task 5 — sanity install

```
$ ./gradlew :app:installRelease
Installing APK 'app-release.apk, app-release.dm' on 'CPH2687 - 16' for :app:release
Installed on 1 device.
BUILD SUCCESSFUL in 9s
```

Simo confirmed on device: real banner ad (no "Test Ad" label), interstitial and both rewarded
placements filled, EEA consent form on fresh install with PRIVACY OPTIONS reopening it from
SETTINGS, themed icon shows an eye, and all six ad unit IDs matched his AdMob console.

### Full test suite against this exact build (2026-08-24, after install)

```
$ ./gradlew --offline :app:testDebugUnitTest
BUILD SUCCESSFUL in 3s
109 tests, 109 passed, 0 failed, 0 skipped

$ ./gradlew --offline :app:connectedDebugAndroidTest
Starting 26 tests on CPH2687 - 16
Finished 26 tests on CPH2687 - 16
BUILD SUCCESSFUL in 1m 19s
26 tests, 26 passed, 0 failed, 0 skipped
```

git status --porcelain at the time of this build: only `.gitignore` (the `tree.txt` ignore rule
from Prompt A) was uncommitted. No app source differs from what shipped in this AAB.

---

## What's left before upload

Everything code- and device-side is done and green. What remains is entirely on the Play Console
side and cannot be verified from this repo:

- [ ] `app-ads.txt` published and crawled (see the checkbox above)
- [ ] Data safety form completed, including the ad-ID and AdServices note above
- [ ] Content rating questionnaire completed
- [ ] Store listing text, screenshots, feature graphic, icon uploaded
- [ ] 12-tester closed test list prepared and opted in

Once those are done: upload `app/build/outputs/bundle/release/app-release.aab` to a closed
testing track. Do **not** regenerate the AAB unless source changes — this one is proven and
matches everything Simo verified on device.

---

## Play Console upload warnings, explained (2026-08-25)

Two warnings appeared on the "review release" screen before saving. Neither blocks saving or
publishing.

### "This release will not be available to any users..."

Expected on every first save of a new release: it has no track/testers assigned yet. Add the
12-tester list to the closed testing track and save again — the warning clears. Nothing to fix
in the build.

### "This App Bundle contains native code, and you've not uploaded debug symbols"

Real, and investigated rather than dismissed. `app/build.gradle.kts`'s `release` block now sets:

```kotlin
ndk {
    debugSymbolLevel = "SYMBOL_TABLE"
}
```

the standard AGP fix — it has AGP bundle native symbol tables into the AAB itself so Play
extracts them automatically, no manual upload ever needed.

It has **no effect here**, and won't. The native code triggering the warning is not this app's —
it is 8 `.so` files (4 ABIs × 2 libraries) pulled in transitively by AndroidX itself:
`androidx.graphics.path` (Compose's hardware-accelerated path rendering) and DataStore's
`datastore_shared_counter`. Forcing `extractReleaseNativeSymbolTables` to re-run with `--info`
shows AGP's own diagnostic for every one of the 8:

```
Unable to extract native debug metadata from .../libandroidx.graphics.path.so because the
native debug metadata has already been stripped.
Unable to extract native debug metadata from .../libdatastore_shared_counter.so because the
native debug metadata has already been stripped.
```

AndroidX ships these `.so` files pre-stripped in its own published AARs — there is no debug
metadata anywhere for this app, or anyone downstream of AndroidX, to supply. This is a known,
common, benign warning for any Compose + DataStore app and is explicitly advisory ("We
recommend...") in Play's own copy, not a rejection or review blocker.

The `debugSymbolLevel` setting was kept anyway: harmless, zero effect today, and correct going
forward if a future dependency ever does ship recoverable symbols.

> **Correction and re-verification, 2026-09-08** — the warning reappeared on the v4 upload, so this
> was re-checked rather than waved through. The paragraph above is half right: the two libraries do
> not agree.
>
> | library | `file` says | `.symtab` | DWARF `.debug_*` |
> | --- | --- | --- | --- |
> | `libandroidx.graphics.path.so` | stripped | none | none |
> | `libdatastore_shared_counter.so` | **not** stripped | **23 function symbols** | **none** |
>
> `file` and AGP simply mean different things by "stripped": `file` looks for `.symtab`, AGP's
> `ExtractNativeDebugMetadataTask` looks for DWARF. Neither library carries DWARF, which is why the
> task emits "already been stripped" for all eight and `mergeReleaseNativeDebugMetadata` reports
> `NO-SOURCE`. Confirmed by a forced `--rerun --info` with NDK 27.1.12297006 installed, so a missing
> NDK is NOT the cause.
>
> **No build-side fix exists.** `debugSymbolLevel` is already `SYMBOL_TABLE`; rebuilding gives the
> identical warning. `FULL` cannot help — it asks for strictly more than `SYMBOL_TABLE`, and there
> is not even that.
>
> The warning CAN be silenced without touching the AAB, by uploading a symbols zip by hand on the
> release page. `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip` is built
> for that purpose from `merged_native_libs`, in Play's `<abi>/<lib>.so` layout. It buys very
> little: only the DataStore library carries anything, the Compose one carries nothing, and neither
> is this app's own code — every line here is Kotlin, and its `proguard.map` is already inside the
> bundle, so Simo's own crashes deobfuscate either way.

AAB rebuilt after this change: `app-release.aab`, **7189503 bytes** (2 bytes different from the
prior build — negligible, from timestamp/metadata normalization, not from any functional
change). Re-ran Proofs 2 and 3 against it: same six real ad unit IDs, zero test IDs, same
privacy URL. Safe to click Save.

---

## Recorded run — versionCode 2 (2026-08-25)

Play rejected the versionCode-1 upload as already used, so `app/build.gradle.kts` moved to
`versionCode = 2` (`versionName` unchanged — no feature or fix in this bump, purely a code
collision). Rebuilt from that one-line change:

```
$ ./gradlew :app:bundleRelease
BUILD SUCCESSFUL in 4m 5s
56 actionable tasks: 23 executed, 33 up-to-date

$ ls -l app/build/outputs/bundle/release/app-release.aab
7189499 bytes

$ ./gradlew :app:assembleRelease
BUILD SUCCESSFUL in 5s

$ aapt2 dump badging app/build/outputs/apk/release/app-release.apk
package: name='com.simobr.donotblink' versionCode='2' versionName='1.0' \
  platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36'

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings | grep -c "3940256099942544"
0

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings \
    | grep -Eo "ca-app-pub-[0-9]{16}[~/][0-9]{10}" | sort -u
ca-app-pub-0000000000000000~0000000000
ca-app-pub-7799898340675704~4432231805
ca-app-pub-7799898340675704/4553088092
ca-app-pub-7799898340675704/5866169761
ca-app-pub-7799898340675704/7043520068
ca-app-pub-7799898340675704/8052410054
ca-app-pub-7799898340675704/9634074714

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings \
    | grep -Eo "https://[a-zA-Z0-9./_-]*Do-Not-Blink[a-zA-Z0-9./_-]*" | sort -u
https://simobr-studio.github.io/Do-Not-Blink_Legal/

$ stat -c '%s bytes' app/build/outputs/mapping/release/mapping.txt
49370141 bytes
```

Same six real ad IDs, zero test IDs, same privacy URL, same mapping size. This is the artifact
to upload — `app/build/outputs/bundle/release/app-release.aab`, versionCode 2.

---

## Recorded run — v3 (versionCode 3 / versionName 1.0.1), 2026-08-25

The fix: `GameViewModel.adsEnabled` was `val adsEnabled: Boolean get() = adHost.adsEnabled` — a
plain property, not Compose state. `Screen.Fail -> FailScreen(bannerEnabled = viewModel.adsEnabled)`
read it once, at the instant the fail screen composed. Ad startup (UMP consent + Mobile Ads init)
runs in the background and commonly takes several seconds; a player who fails fast reaches the
fail screen before it finishes, and since nothing on that screen changes again afterward, nothing
ever told Compose to re-read the value — the banner locked at "false" for that entire visit.

Fix: `adsEnabled` moved into `PlayState` (set alongside `privacyOptionsRequired` when ad startup
completes), `AppRoot` now reads `state.adsEnabled` instead. Verified live on device with two
`uiautomator` dumps of the SAME fail-screen instance, nothing touched in between: at ~10s since
cold launch (mid ad-init) no `WebView`/`AdView` present; at ~19s (9s later, same screen, no
interaction) the banner is present with a `"Test Ad"` label. That transition proves the fix.

```
$ ./gradlew :app:assembleDebug
BUILD SUCCESSFUL in 57s
dnb.useTestAds=false -> REAL IDs (Stage 6, guarded)

$ ./gradlew --offline :app:testDebugUnitTest
109 tests, 109 passed, 0 failed, 0 skipped

$ ./gradlew :app:bundleRelease
BUILD SUCCESSFUL in 3m 43s
56 actionable tasks: 24 executed, 32 up-to-date

$ ls -l app/build/outputs/bundle/release/app-release.aab
7189865 bytes

$ ./gradlew :app:assembleRelease
BUILD SUCCESSFUL in 2s

$ aapt2 dump badging app/build/outputs/apk/release/app-release.apk
package: name='com.simobr.donotblink' versionCode='3' versionName='1.0.1' \
  platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36'

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings | grep -c "3940256099942544"
0

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings \
    | grep -Eo "ca-app-pub-[0-9]{16}[~/][0-9]{10}" | sort -u
ca-app-pub-0000000000000000~0000000000
ca-app-pub-7799898340675704~4432231805
ca-app-pub-7799898340675704/4553088092
ca-app-pub-7799898340675704/5866169761
ca-app-pub-7799898340675704/7043520068
ca-app-pub-7799898340675704/8052410054
ca-app-pub-7799898340675704/9634074714

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings \
    | grep -Eo "https://[a-zA-Z0-9./_-]*Do-Not-Blink[a-zA-Z0-9./_-]*" | sort -u
https://simobr-studio.github.io/Do-Not-Blink_Legal/

$ stat -c '%s bytes' app/build/outputs/mapping/release/mapping.txt
49372315 bytes
```

Same six real ad IDs, zero test IDs, same privacy URL. This is the artifact to upload —
`app/build/outputs/bundle/release/app-release.aab`, versionCode 3 / versionName 1.0.1.

---

## Correction — v3 rebuilt with TEST ADS, 2026-08-25

The "Recorded run — v3" above was built with real ad IDs. Simo then clarified: the intent all
along was test ads for this first Play submission, real IDs 3-4 days later once testing settles.
Rebuilt the SAME versionCode 3 / versionName 1.0.1 (not yet uploaded, so no new code needed) with
`-Pdnb.useTestAds=true`:

```
$ ./gradlew -Pdnb.useTestAds=true :app:bundleRelease
verifyReleaseAdIds: skipped — dnb.useTestAds=true
BUILD SUCCESSFUL in 2m 52s

$ ls -l app/build/outputs/bundle/release/app-release.aab
7190013 bytes

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings \
    | grep -Eo "ca-app-pub-[0-9]{16}[~/][0-9]{10}" | sort -u
ca-app-pub-0000000000000000~0000000000
ca-app-pub-3940256099942544~3347511713    <- Google's public demo App ID
ca-app-pub-3940256099942544/1033173712    <- demo interstitial
ca-app-pub-3940256099942544/5224354917    <- demo rewarded
ca-app-pub-3940256099942544/6300978111    <- demo banner

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings \
    | grep -c "7799898340675704"
0

$ ./gradlew -Pdnb.useTestAds=true :app:assembleRelease
BUILD SUCCESSFUL in 2s

$ aapt2 dump badging app/build/outputs/apk/release/app-release.apk
package: name='com.simobr.donotblink' versionCode='3' versionName='1.0.1'

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings \
    | grep -Eo "https://[a-zA-Z0-9./_-]*Do-Not-Blink[a-zA-Z0-9./_-]*" | sort -u
https://simobr-studio.github.io/Do-Not-Blink_Legal/
```

**This is the artifact to upload for the first Play submission**:
`app/build/outputs/bundle/release/app-release.aab` — versionCode 3, versionName 1.0.1, TEST ADS
(Google's public demo IDs, zero occurrences of the real `7799898340675704` publisher).

## Recorded run — v4 (versionCode 4 / versionName 1.2.0), 2026-09-07

The production candidate. Three features plus the SDK-Index fix. Unlike every build above it, this
one is built with **real** ad IDs — `verifyReleaseAdIds` enforced their presence and shape, as it
always has.

### What is in it

**1. The miss readout.** `RoundClock.judgeRelease` has always computed `Release.errorMs` and the
game has always discarded it: a player who released 4ms late and one who released 400ms early saw
the identical fail screen. A precision game that will not say which SIDE you missed on cannot be
learned, so it reads as luck. The fail screen now prints `EARLY BY 128MS` / `LATE BY 41MS` /
`HELD TOO LONG`, and a perfect release shows its signed error under the flash. New file
`game/Readout.kt`, pure and unit-tested; the data was already in `PlayState`.

**2. The Daily Trial.** Ten seeded rings per calendar day, identical for everyone on that date,
one attempt. A miss does **not** end it — all ten are always played, so results are the same shape
and comparable. Result is `hits/10` plus mean error, shareable as a rendered 1080×1080 PNG. No
backend: `Difficulty.planRound(streak, seed)` was pure and seed-driven from the start, so a date is
a seed. New files `game/DailyTrial.kt`, `ui/daily/DailyResultScreen.kt`, `ui/daily/ShareCard.kt`.

The trial reuses the play surface in a new `Mode.Daily` rather than duplicating it — the gesture,
frame loop, hum and judging are the risky real-time half of this app and are not worth cloning for
a second mode. `Screen.Daily` shares `PlayScreen`'s call site with Home and Play for the same
reason the AppRoot comment already gives: a separate branch would dispose the Canvas and its
`pointerInput` on entry.

**3. The Reflex Test.** Five attempts; the ring arrives after an unpredictable 1400–4600ms and the
gap between the frame it was drawn on and the pointer's own `uptimeMillis` is the score — the same
timebase and the same discipline as a release judgement. Tapping early voids the attempt without
consuming it. New files `game/Twitch.kt`, `ui/twitch/TwitchScreen.kt`.

### Reflex test feedback (added after the first v4 build)

Two new synthesised tones, following the existing rule that **zero audio assets ship in the APK**:

- **`reflexHit`** — a 70ms chirp gliding up an octave, plus a second harmonic. Three pre-rendered
  pitches (740 / 587 / 466 Hz) chosen by `Twitch.chirpTier`, so a faster catch sounds brighter.
  Pre-rendered rather than synthesised per tap: building an `AudioTrack` in the same millisecond
  the player is being timed is the wrong moment for it on low-end hardware. Haptic is
  `EFFECT_HEAVY_CLICK`, heavier than the ring game's `EFFECT_TICK` — catching a signal is one
  decisive event, where a perfect release is the end of a held breath.
- **`reflexTooSoon`** — a 90ms blip gliding DOWN, the reward chirp's exact opposite. Also plays for
  a reading that is discarded (a guess, or one over the abandoned ceiling), so the game never
  congratulates a reading it just threw away.

A glide is what makes these categorically different from the ring game's steady 880Hz perfect tone:
the ear hears movement before it hears pitch. Phase is accumulated per sample rather than computed
from `sin(2*pi*f*t)` — with a changing `f`, that formula sweeps the phase and chirps at roughly
twice the intended rate.

**Nothing sounds or vibrates when the signal ARRIVES,** and that is deliberate: a cue at that
instant gives the player something to react to other than the ring, and auditory reaction is ~40ms
faster than visual. The reading would stop measuring what it claims to.

`Twitch.chirpTier` lives in the pure game object, not in the audio code, so the sound and the grade
word on screen read from one set of boundaries and cannot drift apart. `TwitchScreen` honours the
existing HAPTICS and SOUND settings rows.

#### Verified on device

Both paths confirmed to actually produce audio, via the system volume service observing this app
start and stop playback:

```
$ adb logcat | grep MultiMediaRepository
# on the tap that caught the signal:
22:08:49.760 updateMultiMediaPlayState pid:15636,state:1,packageName=com.simobr.donotblink.debug
22:08:50.073 updateMultiMediaPlayState pid:15636,state:0,packageName=com.simobr.donotblink.debug

# from READY: arm, then tap 250ms later (guaranteed inside the >=1400ms wait):
22:09:xx     updateMultiMediaPlayState pid:15636,state:1 ... then state:0
ATTEMPT 1/5      <- and the attempt was NOT consumed
```

No crashes, no `AudioTrack` errors. `AudioFlinger: AUDIO_OUTPUT_FLAG_FAST denied` appears as a
debug line for every tone — the tracks are 44.1kHz and this device mixes at 48kHz, so the
low-latency fast path is refused and the audio is resampled. That is **pre-existing for every sound
in the app**, not new here, and was left alone deliberately: changing `SampleRateHz` would alter the
already-shipped perfect and fail tones on the eve of a release. Worth revisiting later.

**Not verified: how any of it SOUNDS.** Simo decides that on the device.

### New persistence

`GameStore` gained four values: `lastDailyResult`, `dailyDayStreak`, `dailyBestHits`,
`twitchBestMs`. The daily result is encoded as one preference string (`epochDay|marks|meanErrorMs`)
by `DailyCodec` — DataStore stores primitives and the dependency list is pinned, so the encoding is
explicit and tested. **Anything unparseable decodes to null**, which hands the player a fresh trial
rather than crashing on launch.

### New manifest surface

One `FileProvider`, authority `${applicationId}.shares`, `exported="false"`, exposing exactly one
cache subdirectory (`share_paths.xml`). `ShareCard` clears that directory on every render, so at
most one PNG exists at a time and no player data is reachable through it. **This is the first
non-ad outbound surface in the app** — it is a user-initiated `ACTION_SEND` chooser, still no
network code.

### Three bugs found on the device, not in review

1. **Overlapping touch targets on Home.** The two new mode rows are ~13dp of text but every
   clickable expands to a 48dp minimum target. A 14dp gap chosen to look right left the targets
   overlapping by 21dp — `uiautomator` reported `TODAY'S TRIAL [382,1971][699,2115]` against
   `REFLEX TEST [407,2117][674,2261]` only after the gap went to 36dp; before that they were
   `[382,2037][699,2181]` and `[407,2117][674,2261]`, a 64px overlap in which the lower row
   silently swallowed taps meant for the upper one.
2. **A results race.** `advanceDaily` set `Phase.DailyDone` synchronously but wrote the result
   asynchronously. The result screen composes the instant the phase flips, so on a first-ever
   trial it would have read "no result" and bounced the player home a frame after they finished.
   The result now goes into state in the same breath as the phase. Regression test:
   `the result is in state on the very frame the trial ends` — it deliberately omits
   `advanceUntilIdle()`.
3. **`BEST 17376MS`.** Driving the reflex test by polling meant a 17-second tap, which the app
   recorded as a personal best — the stored best is a MINIMUM, so a lone absurd reading stands as
   the record until it happens to be beaten. Added `Twitch.ABANDONED_CEILING_MS = 2000`; above it
   the reading is reported and discarded. Confirmed on device afterwards: a five-attempt sitting
   recorded only the two readings under the ceiling (`1545`, `1917`) and rejected the other three.

### Device verification (debug build, CPH2687)

Driven with `adb`. Per the note below, `input tap` is near-instant, so every ring it plays fails
EARLY — that is why the trial below scores 0/10.

```
$ adb shell input tap 540 2043            # TODAY'S TRIAL
RING 1/10 · 00

$ (10 taps, 1.6s apart)
after 5:  RING 6/10        <- a miss did NOT end the trial
after 10: result screen

TODAY'S TRIAL / 2026-09-07 / 0/10 / MEAN ERROR 755MS
DAY STREAK 1 · BEST 0/10 / SHARE / HOME / NEXT TRIAL IN 03:27:41
```

`MEAN ERROR 755MS` is the arithmetic working: an instant tap's error is the whole travel time, and
the ten rings ramp 1560ms down to 480ms.

```
$ adb shell input tap 540 1853            # SHARE
chooser: "Share 1 image"
caption: DO NOT BLINK · TRIAL 2026-09-07 / ▯▯▯▯▯▯▯▯▯▯  0/10 / MEAN ERROR 755MS

$ adb shell run-as com.simobr.donotblink.debug ls -l cache/shares/
-rw------- 99344 do-not-blink-20703.png          (1080 x 1080 RGBA)

$ adb logcat -d -s AndroidRuntime:E             # no crashes
```

One attempt per day, confirmed: Home then read `TRIAL 0/10`, and tapping it returned the result
screen rather than starting a second trial.

```
$ adb shell input tap 540 1200            # endless round, instant tap
blinked.
EARLY BY 1560MS                            <- the readout; 1560ms IS the streak-0 travel time
STREAK 00 / BEST 0 / AGAIN / HOME / [Test Ad]
```

Reflex test, five attempts driven by polling for the signal:

```
REFLEX TEST / BEST 1545MS / 1545MS / MEAN 1731MS / TAP TO RUN AGAIN / 1545  1917
```

Two of five readings under the 2000ms ceiling were kept; three were rejected. Mean of 1545 and
1917 is 1731. These are polling latencies, not human reaction times.

**Not verified, and cannot be from here:** how any of this LOOKS. Layout was checked by reading
`uiautomator` bounds, which is geometry, not appearance. The share card was inspected as a pulled
PNG file, which is the artifact, not the rendered screen. Simo decides visual state on the device.

### THE ARTIFACT TO UPLOAD (final, 2026-09-08)

Built from the final source after the monetisation changes and the three scan fixes below. This is
the one to upload; the earlier v4 transcript further down predates all of that.

```
$ ./gradlew --stop
$ ./gradlew :app:testDebugUnitTest
162 tests, 162 passed, 0 failed, 0 skipped

$ ./gradlew :app:bundleRelease :app:assembleRelease
> Task :app:verifyReleaseAdIds        <- ran: REAL ids, no -Pdnb.useTestAds
> Task :app:verifyReleaseSigning      <- ran: the real keystore
BUILD SUCCESSFUL in 3m 21s
63 actionable tasks: 17 executed, 46 up-to-date

app/build/outputs/bundle/release/app-release.aab   7273363 bytes   <- UPLOAD THIS
app/build/outputs/apk/release/app-release.apk      3805069 bytes
app/build/outputs/mapping/release/mapping.txt     50050586 bytes

$ aapt2 dump badging app-release.apk
package: name='com.simobr.donotblink' versionCode='4' versionName='1.2.0' \
  platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36'
targetSdkVersion:'36'

$ unzip -p app-release.aab | strings -a | grep -c "3940256099942544"
0

$ unzip -p app-release.aab | strings -a | grep -Eo "ca-app-pub-[0-9]{16}[~/][0-9]{10}" | sort -u
ca-app-pub-0000000000000000~0000000000     <- the SDK's own placeholder
ca-app-pub-7799898340675704~4432231805
ca-app-pub-7799898340675704/4553088092
ca-app-pub-7799898340675704/5866169761
ca-app-pub-7799898340675704/7043520068
ca-app-pub-7799898340675704/8052410054
ca-app-pub-7799898340675704/9634074714

$ unzip -p app-release.aab | strings -a | grep -Eo "https://[^ ]*Do-Not-Blink[^ ]*" | sort -u
https://simobr-studio.github.io/Do-Not-Blink_Legal/
```

Permissions unchanged from v3 and v1.0.1 — the same 8 plus the `DYNAMIC_RECEIVER` pair. **The
SETTINGS banner added none**: a second placement of an existing ad unit declares nothing new.
Nothing changes on the Data safety form.

FileProvider still survives R8 and resource shrinking: `xml/share_paths` at `0x7f100001`, and the
provider meta-data still points at exactly that id. Feature strings all present in the artifact
(`TODAY'S TRIAL`, `REFLEX TEST`, `EARLY BY`, `HELD TOO LONG`, `STEADY YOUR EYE`,
`WATCH TO CONTINUE`, `NEXT TRIAL IN`).

### The earlier v4 build (superseded)

The first release build was OOM-killed on this machine — the condition the build-environment note
below describes. Rebuilt fully detached with a capped heap, which is what that note prescribes:

```
$ setsid nohup ./gradlew --no-daemon --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx2560m -XX:MaxMetaspaceSize=768m" \
    :app:bundleRelease :app:assembleRelease > release.log 2>&1 < /dev/null & disown

> Task :app:verifyReleaseAdIds        <- ran, i.e. REAL IDs, not -Pdnb.useTestAds
> Task :app:verifyReleaseSigning      <- ran, i.e. the real keystore
BUILD SUCCESSFUL in 21s
63 actionable tasks: 11 executed, 52 up-to-date

app/build/outputs/bundle/release/app-release.aab   7266931 bytes
app/build/outputs/apk/release/app-release.apk      3805069 bytes
app/build/outputs/mapping/release/mapping.txt     49996087 bytes

$ aapt2 dump badging app-release.apk
package: name='com.simobr.donotblink' versionCode='4' versionName='1.2.0' \
  platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36'
targetSdkVersion:'36'

$ unzip -p app-release.aab | strings -a | grep -c "3940256099942544"
0

$ unzip -p app-release.aab | strings -a | grep -Eo "ca-app-pub-[0-9]{16}[~/][0-9]{10}" | sort -u
ca-app-pub-0000000000000000~0000000000
ca-app-pub-7799898340675704~4432231805
ca-app-pub-7799898340675704/4553088092
ca-app-pub-7799898340675704/5866169761
ca-app-pub-7799898340675704/7043520068
ca-app-pub-7799898340675704/8052410054
ca-app-pub-7799898340675704/9634074714

$ unzip -p app-release.aab | strings -a | grep -Eo "https://[^ ]*Do-Not-Blink[^ ]*" | sort -u
https://simobr-studio.github.io/Do-Not-Blink_Legal/
```

Unit tests against this source: **157 tests, 157 passed, 0 failed, 0 skipped** (109 before this
work).

> **The AAB above predates the reflex-test sounds.** It must be rebuilt — see
> **Rebuilding v4** at the end of this section.

### Permissions — UNCHANGED from v3

`aapt2 dump permissions` returns the identical set: `INTERNET`, `ACCESS_NETWORK_STATE`, `AD_ID`,
the three `ACCESS_ADSERVICES_*`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, plus the
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` pair. **The new FileProvider added no permission** — a
provider grants per-URI access at share time and declares nothing. Nothing new for the Data safety
form; the app still collects only the advertising ID.

### The FileProvider survives R8 and resource shrinking

The device test above ran against a DEBUG build, which is neither minified nor resource-shrunk, so
the release wiring was verified separately in the artifact itself:

```
$ aapt2 dump resources app-release.apk | grep -A 1 share_paths
    resource 0x7f100001 xml/share_paths
      () (file) res/l3.xml type=XML

$ aapt2 dump xmltree --file AndroidManifest.xml app-release.apk | grep -A 1 FILE_PROVIDER_PATHS
    A: android:name="android.support.FILE_PROVIDER_PATHS"
    A: android:resource=@0x7f100001            <- points at exactly that resource

$ aapt2 dump xmltree --file res/l3.xml app-release.apk
E: paths
    E: cache-path
      A: name="shares"  A: path="shares/"

$ grep "^androidx.core.content.FileProvider" mapping.txt
androidx.core.content.FileProvider -> androidx.core.content.FileProvider:   <- not renamed
```

`ShareCard` does not appear in `mapping.txt` as a class because R8 inlined the object; its string
literals confirm the code shipped (`SAME TEN RINGS FOR EVERYONE, TODAY ONLY` ×1, `TODAY'S TRIAL`,
`REFLEX TEST`, `EARLY BY `, `LATE BY `, `HELD TOO LONG`, `WERE YOU LOOKING`, `NEXT TRIAL IN `,
`.shares` ×2, all present). The full `DO NOT BLINK · TRIAL` caption greps as 0 only because the
`·` is UTF-8 `C2 B7` and breaks `strings`' ASCII run — `DO NOT BLINK` itself appears twice.

**Still unproven: the share on a MINIFIED build at runtime.** Installing the release APK on the
test device failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — the device holds the Play
closed-test build, and clearing it would destroy that install's data mid-test. Either test the
share from the Play internal-testing track after upload, or uninstall the closed-test build
deliberately and `installRelease`.

### Monetisation changes (v1.2.0, at Simo's request)

Three asks: more interstitials, a banner somewhere beyond the fail screen, and the rewarded
continue offered on every run rather than only good ones. All three done. **Every UX guardrail was
kept** — the numbers moved, the exclusions did not, because the exclusions are what a Disruptive
Ads strike is actually about.

**Interstitial cadence**, `ads/AdPolicy.kt`:

| Clause | Was | Now |
| --- | --- | --- |
| `MIN_RUNS_BETWEEN_INTERSTITIALS` | 3 | **2** |
| `MIN_SECONDS_BETWEEN_INTERSTITIALS` | 90 | **60** |
| `CLEAN_RUNS_AT_SESSION_START` | 4 | **3** |
| `MAX_INTERSTITIALS_PER_SESSION` | 6 | **8** |
| never after a personal best | kept | **kept** |
| never after a rewarded continue | kept | **kept** |

The seconds clause is what actually paces this — runs can end in eight seconds, so the run count
alone is not a cadence. Practical effect: first ad no earlier than the 3rd run of a session, then
at most one per minute, ceiling eight.

**A second interstitial site**: a finished reflex sitting (five attempts, summary up) now feeds the
same gate, via `GameViewModel.onReflexSittingCompleted()`. It is a natural break by construction —
the test is over, nothing is in flight — and it answers to the SAME cadence, so a player bouncing
between modes cannot be shown more than either mode alone would allow.

The trap avoided, and now pinned by two tests: this could not route through `AdSession.onRunEnded`.
That method sets `previousRunWasPersonalBest` / `previousRunUsedRewardedContinue`, so calling it
with `false, false` after a sitting would have **silently cancelled the protection a personal best
had just earned** — play a blinder, wander into the reflex test, come back and get taxed anyway.
`onSideActivityEnded()` moves the two counters and touches neither flag.

**The rewarded continue is no longer reserved for good runs.** It required `streak >= 8` AND
`streak >= bestStreak - 3`, so a player whose best was 40 never saw it below streak 37. Both gates
are gone; the floor is now `streak >= 1`. `bestStreak` left the signature entirely — nothing reads
it any more.

The one floor kept is deliberate: at streak 0 a continue resumes at streak 0, which is identical to
pressing AGAIN. Offering an ad for an outcome the player can have for free is friction with no
benefit, and reads as a dark pattern rather than a feature.

**Banner in SETTINGS**, the second banner surface in the app. It follows the fail screen's rule
exactly — the slot is reserved at its true SDK-reported height whether or not an ad ever fills, and
`BUILD x.y.z` is pushed up by that height, so nothing on the screen moves depending on fill.

Not done, and deliberately: **no interstitial anywhere in the Daily Trial.** CLAUDE.md pins "a trial
never touches the endless best streak, the title ladder, or the interstitial cadence", and the trial
is the retention feature — the one thing that must stay clean. Say so explicitly if you want that
reversed.

### Three bugs found by the pre-release scan

1. **The reflex test could silently eat the tap it exists to measure.** Its gesture handler was
   `Modifier.pointerInput(step)`. `pointerInput` cancels and restarts its coroutine whenever a key
   changes, and for a window around that restart the node is registered to nothing — and `step`
   changes on nearly every beat of that screen. A tap landing in the gap was not mistimed, it was
   dropped: nothing happened at all. Most dangerous at exactly the Ready→Signal transition, i.e.
   the fastest reactions. Now keyed on `Unit`, so the loop runs uninterrupted for the screen's whole
   life and reads `step` fresh each iteration — the same discipline `PlayScreen.roundInput` already
   documents and relies on.
2. **`PlayScreen`'s frame loop could go stale on a mode change.** `animating` reads `state.mode`,
   but the `LaunchedEffect` driving the loop was keyed only on `(roundId, phase)`. Safe today only
   because every caller happens to flip mode alongside one of those. `state.mode` added as a key.
3. **An orphaned KDoc** in `AppRoot.kt`: `openUrl`'s documentation had been left stranded above
   `shareDailyResult`, describing the wrong function, with `openUrl` left undocumented. Restored.

Also fixed while revising the above: the reflex screen's now-permanent gesture block captured
`hapticsEnabled` / `soundEnabled` as plain parameters, which would have frozen them at first
composition. Both are `rememberUpdatedState` now, like `onReaction` already was.

### Device verification of the monetisation changes (debug build, CPH2687)

**Banner in SETTINGS** — `uiautomator` bounds, which is geometry, not appearance:

```
LICENCES           [78,1424][316,1610]
BUILD 1.2.0-debug  [78,1946][472,1982]     <- ends at 1982
banner slot        [0,2100][1080,2268]     <- starts at 2100, 118px of clearance
Test Ad            [458,2100][621,2161]    <- the slot actually filled
```

No row overlaps the slot, and BUILD sits clear above it.

**The continue offer at a low streak** — the actual behaviour change. Simo's stored best is 20, so
the old policy needed streak >= 17 here and this run would have gone straight to the fail screen:

```
$ adb shell input swipe 540 1200 541 1200 1560   # a real hold: ring 1 is 1560ms +/- 162ms
STREAK 01                                         # PERFECT
$ adb shell input tap 540 1200                    # instant tap -> EARLY fail at streak 1
ONCE PER RUN / 01 / STEADY YOUR EYE / WATCH TO CONTINUE / ACCEPT THE BLINK
```

(A 1px swipe with a duration registers on this device; the zero-distance swipe noted below does
not. That is how a scripted PERFECT release is possible at all.)

**The reflex-sitting interstitial, and its guardrail.** Two sittings were played back to back on a
freshly started process:

```
sitting 1  -> summary, NO ad
             (runsThisSession was 2 — below CLEAN_RUNS_AT_SESSION_START, correctly held back)
sitting 2  -> runsThisSession reaches 3, and:
23:58:37.907 AppSenseClient: {pkgName:com.simobr.donotblink.debug,
                              activityName:com.google.android.gms.ads.AdActivity, winMode:1}
23:58:38.651 [AdActivity] change focus to true      <- full screen, 1080x2400
```

After dismissal the reflex summary was still underneath and intact, as designed. Both the feature
and the session-start protection are therefore confirmed, not assumed.

**No crash from this app throughout.** `FATAL EXCEPTION` entries do appear in that logcat, and all
of them are `com.android.commands.uiautomator.DumpCommand` — the polling harness colliding with
itself ("UiAutomationService already registered"). Zero frames from `com.simobr.donotblink`,
confirmed by grepping the crash blocks for the package name.

### Known, pre-existing, NOT changed

`ContinueOfferScreen` shows a fixed `ONCE PER RUN` badge, but `MAX_CONTINUES_PER_RUN` is 2 — a
second continue is possible when a continued run passes the record it started with. The badge has
been slightly inaccurate since Stage 5 and shipped that way in 1.0.1. It matters a little more now
only because the offer appears far more often. Left alone on purpose: it is product copy Simo has
already reviewed, and changing it unilaterally on the eve of the production upload is not this
change's business. Worth a decision before or after upload, not during.

### Rebuilding v4 (the commands)

The recorded AAB above was built BEFORE the reflex-test sounds, so it must be rebuilt. Nothing
about the version changes — versionCode stays 4, versionName stays 1.2.0, because nothing has been
uploaded to Play yet. Bump only if an upload has already happened.

```
./gradlew --stop
./gradlew :app:testDebugUnitTest
./gradlew :app:bundleRelease :app:assembleRelease
```

That is all. No `-Pdnb.useTestAds` — this is production, and its absence is what makes
`verifyReleaseAdIds` demand the six real IDs from `local.properties`.

If the build is OOM-killed on this machine (11.8GB, and the daemon is the first thing killed when
a browser is open), run it detached instead and poll the log:

```
setsid nohup ./gradlew --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx2560m -XX:MaxMetaspaceSize=768m" \
  :app:bundleRelease :app:assembleRelease > /tmp/dnb-release.log 2>&1 < /dev/null & disown

tail -f /tmp/dnb-release.log
```

Upload `app/build/outputs/bundle/release/app-release.aab`. Re-run the four proofs above against
the new artifact first — in particular the test-ID grep must still print `0`.

### Before uploading

- [ ] **Test the SHARE button on a release (minified) build.** See directly above — this is the
      one path proven only in debug.
- [ ] Play a trial by HAND, with real releases, so the grid shows lit rings and not ten dead ones.
- [ ] Confirm the share card looks right out of WhatsApp, which recompresses.
- [ ] Confirm the fail-screen readout reads well at low panel brightness — it sits on `LabelMid`.
- [ ] Store listing: new screenshots. The app has two new modes and the current shots show neither.

### Before switching to real IDs in 3-4 days

Rebuild WITHOUT the flag — `./gradlew :app:bundleRelease` (no `-Pdnb.useTestAds`), which defaults
to `false` and requires the real six in `local.properties`, exactly as `verifyReleaseAdIds` has
always enforced. **Increment `versionCode` first** (Play will reject a repeat). Re-run the
test-ID grep from the top of this document on that build before uploading — a real submission
must show zero test-publisher (`3940256099942544`) hits.
