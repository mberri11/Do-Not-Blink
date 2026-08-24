# DO NOT BLINK — release checklist

Nothing here is performed by this file. Every line is a thing a human ticks after running the
command next to it and reading the output. Unticked means not done, not "probably fine".

Package: `com.simobr.donotblink` · versionCode `1` · versionName `1.0`

---

## Before the build

- [ ] `local.properties` (git-ignored, repo root) carries all six real AdMob IDs:
      `ADMOB_APP_ID`, `ADMOB_UNIT_BANNER`, `ADMOB_UNIT_INTERSTITIAL`,
      `ADMOB_UNIT_REWARDED_CONTINUE`, `ADMOB_UNIT_REWARDED_PHOSPHOR`, `ADMOB_UNIT_REWARDED_TITLES`.
      `:app:verifyReleaseAdIds` refuses to package without them, refuses a Google test ID, and
      refuses a malformed one — but it cannot tell a *wrong* real ID from a right one.
- [ ] `~/.gradle/gradle.properties` (machine-global, never committed) carries
      `SIMOBR_KEYSTORE`, `SIMOBR_KEYSTORE_PASS`, `DNB_KEY_ALIAS`, `DNB_KEY_PASS`.
      `:app:verifyReleaseSigning` refuses to package without them and never falls back to the
      debug key.
- [ ] The privacy policy URL in `gradle.properties`
      (`donotblink.privacyPolicyUrl`) is live in a browser **before** upload — the Play listing,
      the AdMob app settings and the app's SETTINGS row must all point at the same URL.
- [ ] `app-ads.txt` is published at the root of the developer website declared in AdMob, and
      AdMob's crawler has picked it up (AdMob → Apps → app-ads.txt shows "Authorized").

## The build

```
./gradlew :app:bundleRelease
```

- [ ] Exit code is `0`.
- [ ] AAB is at `app/build/outputs/bundle/release/app-release.aab`; note its byte size here: ______
- [ ] R8 mapping file exists at `app/build/outputs/mapping/release/mapping.txt` and is uploaded
      with the bundle (Play Console does this automatically for an AAB; confirm it appears under
      App bundle explorer → Downloads → ReTrace mapping file).

## Proving what is in the bundle

### Target API 36

`aapt2` cannot read an AAB. `bundletool` can:

```
bundletool dump manifest --bundle=app/build/outputs/bundle/release/app-release.aab \
  --xpath=/manifest/uses-sdk/@android:targetSdkVersion
```

- [ ] Prints `36`.

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

- [ ] Prints `0`.

Run this again on the real credentialed build before upload — the number below was produced on
a verification build, not on the artifact you are shipping.

It printed `0`. See **Recorded run** for the full transcript.

## Play Console

- [ ] **Data safety**: advertising ID declared as collected (by the Google Mobile Ads SDK, for
      advertising). Nothing else is collected — the game is fully offline, has no analytics SDK,
      no Firebase, no Crashlytics, no account, and no IAP. Best streak and settings live in
      DataStore on the device and never leave it.
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

- [ ] Installs on a physical device.
- [ ] Themed icons ON (long-press home → Wallpaper & style → Themed icons): the launcher shows
      an **eye**, not a solid almond blob.
- [ ] Play a run to a fail: the banner loads and shows a **real** ad with **no "Test Ad" label**.
- [ ] SETTINGS → PRIVACY POLICY opens the live page in a browser.
- [ ] Under an EEA test geo, SETTINGS shows **PRIVACY OPTIONS** and it reopens the consent form.
      Outside the EEA the row must not be there at all.
- [ ] No crash on cold start. `MobileAdsInitProvider` throws at process start if the AdMob App ID
      meta-data is missing, so a bad `ADMOB_APP_ID` shows up here immediately.

---

## Recorded run

Filled in by Stage 6 on 2026-08-23, on a **verification bundle** built with throwaway
well-formed ad IDs and the debug keystore. It proves the pipeline — R8, resource shrinking,
signing wiring, and the absence of test IDs — and is **not** a shippable artifact. Every box
above still has to be ticked against the real credentialed build.

```
$ ./gradlew :app:bundleRelease
BUILD SUCCESSFUL in 12m 55s
56 actionable tasks: 12 executed, 44 up-to-date

$ ls -l app/build/outputs/bundle/release/app-release.aab
-rw-rw-r-- 1 mberri mberri 7183281 Aug 23 23:05 app-release.aab
                                   7183281 bytes

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings -a \
    | grep -c "ca-app-pub-3940256099942544"
0

$ unzip -p app/build/outputs/bundle/release/app-release.aab | strings -a \
    | grep -oE "ca-app-pub-[0-9]{16}[~/][0-9]{10}" | sort -u
ca-app-pub-0000000000000000~0000000000     <- ships inside the Google Mobile Ads SDK itself
ca-app-pub-1111111111111111~2222222222     <- the six throwaway IDs, as configured
ca-app-pub-1111111111111111/3333333333
ca-app-pub-1111111111111111/4444444444
ca-app-pub-1111111111111111/5555555555
ca-app-pub-1111111111111111/6666666666
ca-app-pub-1111111111111111/7777777777

$ ls -l app/build/outputs/mapping/release/mapping.txt
-rw-rw-r-- 1 mberri mberri 49333370 Aug 23 23:05 mapping.txt
```

`bundletool` is not on this machine, so target API was proven on the release APK instead —
the equivalent check documented above:

```
$ ./gradlew :app:assembleRelease
BUILD SUCCESSFUL in 8s

$ aapt2 dump badging app/build/outputs/apk/release/app-release.apk
package: name='com.simobr.donotblink' versionCode='1' versionName='1.0' \
  platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36'
targetSdkVersion:'36'

$ unzip -p app/build/outputs/apk/release/app-release.apk | strings -a \
    | grep -c "ca-app-pub-3940256099942544"
0

$ stat -c '%s bytes' app/build/outputs/apk/release/app-release.apk
3765685 bytes                              <- R8 + shrinkResources, against 17894821 for debug

$ apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
```

That last line is why both artifacts were **deleted** immediately after these commands ran: they
were signed with the debug key. `SIMOBR_KEYSTORE` and friends were passed on the command line for
this run only and were never written to any file.

And the guard, with no credentials present at all:

```
$ ./gradlew :app:bundleRelease
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':app:verifyReleaseAdIds'.
> Refusing to package a release build with these AdMob problems:
    - ADMOB_APP_ID is missing
    - ADMOB_UNIT_BANNER is missing
    - ADMOB_UNIT_INTERSTITIAL is missing
    - ADMOB_UNIT_REWARDED_CONTINUE is missing
    - ADMOB_UNIT_REWARDED_PHOSPHOR is missing
    - ADMOB_UNIT_REWARDED_TITLES is missing
exit code 1
```
