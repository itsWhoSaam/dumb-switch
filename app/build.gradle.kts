import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.houseofai.dumbswitch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.houseofai.dumbswitch"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
