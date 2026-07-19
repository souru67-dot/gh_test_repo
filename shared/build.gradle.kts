plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

/**
 * Cross-platform core shared by the Android app and (Phase: iOS) a SwiftUI app.
 * Currently hosts the pure colour-classification domain — deliberately free of
 * any platform API so it compiles for both Android and iOS unchanged.
 */
kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    // iOS targets. These are configured on all hosts but only *built* on macOS;
    // an Android-only CI (assembleDebug) never triggers the native compile.
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SharedColor"
            isStatic = true
        }
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

android {
    namespace = "com.souru.colorhunt.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
