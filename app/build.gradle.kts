import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Real AdMob identifiers live in local.properties, which is git-ignored. A real unit ID is never
 * committed. Debug builds always use Google's public test IDs; a release build with a blank ID
 * simply never loads that surface — the game is never blocked by an ad.
 */
val adProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun adProperty(name: String): String = (adProperties.getProperty(name) ?: "").trim()

val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
val TEST_BANNER_UNIT = "ca-app-pub-3940256099942544/6300978111"
val TEST_INTERSTITIAL_UNIT = "ca-app-pub-3940256099942544/1033173712"
val TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"

/**
 * An escape hatch for a release-style build (minified, signed) that must not show real ads —
 * a review pipeline, a demo, a QA build. `-Pdnb.useTestAds=true`, or `dnb.useTestAds=true` in a
 * gradle.properties. Release only: debug already always uses the test constants unconditionally,
 * and this property has no effect there. Default false leaves Stage 6's behaviour untouched.
 */
val useTestAds: Boolean =
    providers.gradleProperty("dnb.useTestAds").getOrElse("false").toBoolean()

/** Last 4 characters only — enough to eyeball which ID landed without printing the whole thing. */
fun maskAdId(value: String): String = if (value.isEmpty()) "<empty>" else "…" + value.takeLast(4)

logger.lifecycle(
    "dnb.useTestAds=$useTestAds -> " +
        if (useTestAds) "TEST ADS (Google demo IDs, verifyReleaseAdIds skipped)" else "REAL IDs (Stage 6, guarded)"
)
run {
    val appIdForLog = if (useTestAds) TEST_APP_ID else adProperty("ADMOB_APP_ID")
    val bannerForLog = if (useTestAds) TEST_BANNER_UNIT else adProperty("ADMOB_UNIT_BANNER")
    val interstitialForLog = if (useTestAds) TEST_INTERSTITIAL_UNIT else adProperty("ADMOB_UNIT_INTERSTITIAL")
    val rewardedContinueForLog = if (useTestAds) TEST_REWARDED_UNIT else adProperty("ADMOB_UNIT_REWARDED_CONTINUE")
    val rewardedPhosphorForLog = if (useTestAds) TEST_REWARDED_UNIT else adProperty("ADMOB_UNIT_REWARDED_PHOSPHOR")
    val rewardedTitlesForLog = if (useTestAds) TEST_REWARDED_UNIT else adProperty("ADMOB_UNIT_REWARDED_TITLES")
    logger.lifecycle("  APP_ID (dnb_app_id)                   = ${maskAdId(appIdForLog)}")
    logger.lifecycle("  BANNER (dnb_banner_fail)               = ${maskAdId(bannerForLog)}")
    logger.lifecycle("  INTERSTITIAL (dnb_interstitial_round)  = ${maskAdId(interstitialForLog)}")
    logger.lifecycle("  REWARDED_CONTINUE (dnb_rewarded_continue) = ${maskAdId(rewardedContinueForLog)}")
    logger.lifecycle("  REWARDED_PHOSPHOR (dnb_rewarded_phosphor) = ${maskAdId(rewardedPhosphorForLog)}")
    logger.lifecycle("  REWARDED_TITLES (dnb_rewarded_titles)  = ${maskAdId(rewardedTitlesForLog)}")
}

/** A typo in a pasted ID is the commonest way an indie release ships with no fill at all. */
val APP_ID_SHAPE = Regex("""^ca-app-pub-\d{16}~\d{10}$""")
val UNIT_ID_SHAPE = Regex("""^ca-app-pub-\d{16}/\d{10}$""")

/**
 * The five unit IDs a release must carry, as (gradle-visible name, value, the test ID it must not
 * be). Evaluated at configuration time; VALIDATED at execution time by `verifyReleaseAdIds`, so a
 * plain `./gradlew test` on a clean checkout is never blocked by a missing credential.
 */
val releaseUnitIds: List<Triple<String, String, String>> by lazy {
    listOf(
        Triple("ADMOB_UNIT_BANNER", adProperty("ADMOB_UNIT_BANNER"), TEST_BANNER_UNIT),
        Triple("ADMOB_UNIT_INTERSTITIAL", adProperty("ADMOB_UNIT_INTERSTITIAL"), TEST_INTERSTITIAL_UNIT),
        Triple("ADMOB_UNIT_REWARDED_CONTINUE", adProperty("ADMOB_UNIT_REWARDED_CONTINUE"), TEST_REWARDED_UNIT),
        Triple("ADMOB_UNIT_REWARDED_PHOSPHOR", adProperty("ADMOB_UNIT_REWARDED_PHOSPHOR"), TEST_REWARDED_UNIT),
        Triple("ADMOB_UNIT_REWARDED_TITLES", adProperty("ADMOB_UNIT_REWARDED_TITLES"), TEST_REWARDED_UNIT),
    )
}

/**
 * Release signing. Read through `providers.gradleProperty` so these resolve from
 * ~/.gradle/gradle.properties — machine-global, outside the repo — and never from a committed file.
 * A missing one is NOT a fall back to the debug key: it is a failed build, named property first.
 */
val signingProperties = listOf(
    "SIMOBR_KEYSTORE", "SIMOBR_KEYSTORE_PASS", "DNB_KEY_ALIAS", "DNB_KEY_PASS",
)

fun signingProperty(name: String): String =
    (providers.gradleProperty(name).orNull ?: "").trim()

val missingSigningProperties: List<String> = signingProperties.filter { signingProperty(it).isEmpty() }

/**
 * The published privacy policy, from gradle.properties. Not a secret — the Play listing shows it
 * to everyone — so it is committed, and the settings row reads it through BuildConfig.
 */
val privacyPolicyUrl: String =
    (providers.gradleProperty("donotblink.privacyPolicyUrl").orNull ?: "").trim()


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.simobr.donotblink"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.simobr.donotblink"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Public information, so it lives in gradle.properties rather than local.properties.
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
    }


    signingConfigs {
        create("release") {
            // Configured only when every property is present. When one is missing this config is
            // left empty and `verifyReleaseSigning` fails the build by name — silently signing a
            // store upload with the debug key is far worse than not building at all.
            if (missingSigningProperties.isEmpty()) {
                storeFile = file(signingProperty("SIMOBR_KEYSTORE"))
                storePassword = signingProperty("SIMOBR_KEYSTORE_PASS")
                keyAlias = signingProperty("DNB_KEY_ALIAS")
                keyPassword = signingProperty("DNB_KEY_PASS")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            manifestPlaceholders["admobAppId"] = TEST_APP_ID
            buildConfigField("String", "AD_UNIT_BANNER", "\"$TEST_BANNER_UNIT\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"$TEST_INTERSTITIAL_UNIT\"")
            buildConfigField("String", "AD_UNIT_REWARDED_CONTINUE", "\"$TEST_REWARDED_UNIT\"")
            buildConfigField("String", "AD_UNIT_REWARDED_PHOSPHOR", "\"$TEST_REWARDED_UNIT\"")
            buildConfigField("String", "AD_UNIT_REWARDED_TITLES", "\"$TEST_REWARDED_UNIT\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The .so files here are AndroidX's own (Compose's graphics.path, DataStore's shared
            // counter) — nothing this app or the ad SDK ships. SYMBOL_TABLE has AGP bundle their
            // symbol tables straight into the AAB, so Play extracts them on upload: readable
            // native crash stacks in Android vitals, no separate manual upload ever needed.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            if (missingSigningProperties.isEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }

            if (useTestAds) {
                // Deliberate: Google's own demo IDs, the same ones the debug build type always
                // carries. verifyReleaseAdIds is skipped below to match — it exists to catch an
                // ACCIDENTAL test ID, not this intentional one.
                manifestPlaceholders["admobAppId"] = TEST_APP_ID
                buildConfigField("String", "AD_UNIT_BANNER", "\"$TEST_BANNER_UNIT\"")
                buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"$TEST_INTERSTITIAL_UNIT\"")
                buildConfigField("String", "AD_UNIT_REWARDED_CONTINUE", "\"$TEST_REWARDED_UNIT\"")
                buildConfigField("String", "AD_UNIT_REWARDED_PHOSPHOR", "\"$TEST_REWARDED_UNIT\"")
                buildConfigField("String", "AD_UNIT_REWARDED_TITLES", "\"$TEST_REWARDED_UNIT\"")
            } else {
                val appId = adProperty("ADMOB_APP_ID")
                // NOT `.ifEmpty { TEST_APP_ID }`. A forgotten local.properties used to produce a
                // perfectly valid, uploadable AAB carrying Google's public test App ID — an
                // invalid-traffic incident waiting to happen. The placeholder below is deliberately
                // not an ad ID at all, and `verifyReleaseAdIds` blocks packaging long before it could
                // reach a device.
                manifestPlaceholders["admobAppId"] = appId.ifEmpty { "ADMOB_APP_ID_MISSING" }
                buildConfigField("String", "AD_UNIT_BANNER", "\"${adProperty("ADMOB_UNIT_BANNER")}\"")
                buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"${adProperty("ADMOB_UNIT_INTERSTITIAL")}\"")
                buildConfigField("String", "AD_UNIT_REWARDED_CONTINUE", "\"${adProperty("ADMOB_UNIT_REWARDED_CONTINUE")}\"")
                buildConfigField("String", "AD_UNIT_REWARDED_PHOSPHOR", "\"${adProperty("ADMOB_UNIT_REWARDED_PHOSPHOR")}\"")
                buildConfigField("String", "AD_UNIT_REWARDED_TITLES", "\"${adProperty("ADMOB_UNIT_REWARDED_TITLES")}\"")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // The settings screen prints the build name; it is never hardcoded.
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// ---- release guards -----------------------------------------------------------------------------
//
// Both tasks fail LOUDLY and both run only when a release artifact is actually being packaged.
// They are wired to the packaging tasks rather than to `preReleaseBuild`, because the latter is in
// the graph of `./gradlew test` and a clean checkout with no credentials must still run its tests.

val verifyReleaseAdIds = tasks.register("verifyReleaseAdIds") {
    group = "verification"
    description = "Fails the release build unless every AdMob ID is present, well-formed and not a test ID."
    doLast {
        if (useTestAds) {
            // Intentional test ads (dnb.useTestAds=true): the whole point of this task is to catch
            // an ACCIDENTAL test ID reaching a real release, which does not apply here.
            logger.lifecycle("verifyReleaseAdIds: skipped — dnb.useTestAds=true")
            return@doLast
        }

        val problems = mutableListOf<String>()

        val appId = adProperty("ADMOB_APP_ID")
        when {
            appId.isEmpty() -> problems += "ADMOB_APP_ID is missing"
            appId == TEST_APP_ID -> problems += "ADMOB_APP_ID is Google's public TEST App ID"
            !APP_ID_SHAPE.matches(appId) ->
                problems += "ADMOB_APP_ID '$appId' is malformed (expected ca-app-pub-<16 digits>~<10 digits>)"
        }

        releaseUnitIds.forEach { (name, value, testValue) ->
            when {
                value.isEmpty() -> problems += "$name is missing"
                value == testValue -> problems += "$name is a Google TEST ad unit ID"
                !UNIT_ID_SHAPE.matches(value) ->
                    problems += "$name '$value' is malformed (expected ca-app-pub-<16 digits>/<10 digits>)"
            }
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Refusing to package a release build with these AdMob problems:")
                    problems.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("All six live in local.properties at the repo root (git-ignored):")
                    appendLine("  ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY")
                    releaseUnitIds.forEach { (name, _, _) ->
                        appendLine("  $name=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY")
                    }
                }.trim()
            )
        }
    }
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Fails the release build unless the upload key is configured. Never falls back to debug."
    doLast {
        if (missingSigningProperties.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Refusing to package an unsigned release. Missing:")
                    missingSigningProperties.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("These belong in ~/.gradle/gradle.properties — machine-global, never committed.")
                }.trim()
            )
        }
        val keystore = file(signingProperty("SIMOBR_KEYSTORE"))
        if (!keystore.isFile) {
            throw GradleException("SIMOBR_KEYSTORE points at ${keystore.absolutePath}, which is not a file.")
        }
    }
}

tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }.configureEach {
    dependsOn(verifyReleaseAdIds, verifyReleaseSigning)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    // Not used directly: play-services-basement:18.9.0 transitively pulls in
    // androidx.fragment:fragment:1.1.0, which Play's SDK Index flags as outdated. Declaring a
    // current version here wins Gradle's conflict resolution over that transitive pin.
    implementation(libs.androidx.fragment.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
