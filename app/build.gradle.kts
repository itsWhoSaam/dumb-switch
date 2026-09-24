import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Committed with the keystore on purpose — a personal, sideload-only project trades
// secrecy for signature stability across machines and CI runs. See README "Release
// installs"; rotate to CI secrets if distribution ever changes.
val releaseStorePassword = project.property("RELEASE_STORE_PASSWORD") as String

android {
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("config/release.keystore")
            storePassword = releaseStorePassword
            keyAlias = "dumb-switch"
            keyPassword = releaseStorePassword
        }
    }

    namespace = "com.houseofai.dumbswitch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.houseofai.dumbswitch"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            // Minification stays off — the APK is tiny and R8 buys nothing here.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Plain-JVM unit tests run against the mockable android.jar: return defaults instead
        // of throwing "not mocked" for framework calls that slip past the test fakes.
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // Deprecation warnings are reviewed, not suppressed blanketly; see AllowlistResolver
        // for the one intentional use of a deprecated API.
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
}
