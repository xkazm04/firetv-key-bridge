plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Fire TV Key Bridge: one WebView that loads any web app fullscreen and hands the remote's keys to the page.
 * No Compose, no AndroidX, no runtime dependency: the page draws everything.
 */
android {
    namespace = "dev.xkazm04.keybridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.xkazm04.keybridge"
        // Fire OS 7 is Android 9 (API 28); Fire OS 8 is Android 11 (API 30).
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // The demo page is the one source of truth: it is also packaged as the built-in "Try the demo" target.
    sourceSets["main"].assets.srcDir("../docs/demo")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
