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
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            val appId = adProperty("ADMOB_APP_ID")
            if (appId.isEmpty()) {
                logger.warn("RELEASE: ADMOB_APP_ID missing from local.properties — ads will not serve.")
            }
            manifestPlaceholders["admobAppId"] = appId.ifEmpty { TEST_APP_ID }
            buildConfigField("String", "AD_UNIT_BANNER", "\"${adProperty("ADMOB_UNIT_BANNER")}\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"${adProperty("ADMOB_UNIT_INTERSTITIAL")}\"")
            buildConfigField("String", "AD_UNIT_REWARDED_CONTINUE", "\"${adProperty("ADMOB_UNIT_REWARDED_CONTINUE")}\"")
            buildConfigField("String", "AD_UNIT_REWARDED_PHOSPHOR", "\"${adProperty("ADMOB_UNIT_REWARDED_PHOSPHOR")}\"")
            buildConfigField("String", "AD_UNIT_REWARDED_TITLES", "\"${adProperty("ADMOB_UNIT_REWARDED_TITLES")}\"")
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
