import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.nav.safeargs)
}

// ---------------------------------------------------------------------------
// Load signing properties from a file that is NOT in version control.
// Create app/release.properties with:
//   storeFile=../keystore/release.jks
//   storePassword=…
//   keyAlias=…
//   keyPassword=…
// ---------------------------------------------------------------------------
val releasePropsFile = file("release.properties")
val releaseProps = Properties().apply {
    if (releasePropsFile.exists()) load(releasePropsFile.inputStream())
}

val admobAppId = releaseProps.getProperty("admobAppId", "ca-app-pub-3940256099942544~3347511713")
val admobBannerId = releaseProps.getProperty("admobBannerId", "ca-app-pub-3940256099942544/6300978111")
val admobInterstitialId = releaseProps.getProperty("admobInterstitialId", "ca-app-pub-3940256099942544/1033173712")
val admobRewardedId = releaseProps.getProperty("admobRewardedId", "ca-app-pub-3940256099942544/5224354917")

android {
    namespace  = "com.liquidcolorsort"
    compileSdk = 35

    defaultConfig {
        applicationId   = "com.liquidcolorsort"
        minSdk          = 24
        targetSdk       = 35
        versionCode     = 1
        versionName     = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AdMob App ID (test value — replace before publishing)
        manifestPlaceholders["admobAppId"] = admobAppId
    }

    signingConfigs {
        create("release") {
            if (releasePropsFile.exists()) {
                storeFile     = file(releaseProps["storeFile"] as String)
                storePassword = releaseProps["storePassword"] as String
                keyAlias      = releaseProps["keyAlias"] as String
                keyPassword   = releaseProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"

            // Google AdMob test ad-unit IDs — safe to commit
            buildConfigField("String", "ADMOB_BANNER_ID",       "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("String", "ADMOB_REWARDED_ID",     "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("Boolean", "ADS_ENABLED", "true")
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            buildConfigField("String", "ADMOB_BANNER_ID",       "\"$admobBannerId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
            buildConfigField("String", "ADMOB_REWARDED_ID",     "\"$admobRewardedId\"")
            buildConfigField("Boolean", "ADS_ENABLED", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose     = true
        buildConfig = true
        viewBinding = true   // used by XML layouts (TubeView host)
    }

    // JUnit 5 support for unit tests
    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
            it.testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}

dependencies {
    // ── Core ─────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // ── Lifecycle / ViewModel ─────────────────────────────────────────────────
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ── Navigation ────────────────────────────────────────────────────────────
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // ── Jetpack Compose (used on non-game screens) ────────────────────────────
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.hilt.navigation.compose)

    // ── Hilt dependency injection ─────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // ── Room ──────────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── DataStore preferences ─────────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── AdMob ─────────────────────────────────────────────────────────────────
    implementation(libs.play.services.ads)

    // FlexboxLayout (tube grid wrapping)
    implementation(libs.flexbox)

    // ── Unit tests (JVM — JUnit 5 + MockK) ────────────────────────────────────
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    // ── Instrumented tests ────────────────────────────────────────────────────
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)

    // ── Debug only ────────────────────────────────────────────────────────────
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
