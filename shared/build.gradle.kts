plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    kotlin("native.cocoapods") version "2.4.10"
}

group = "com.metahelper"
version = "1.0"

kotlin {
    androidTarget()

    val iosTarget = iosX64("ios")
    iosTarget.binaries.framework {
        baseName = "MetaHelperShared"
        isStatic = true
    }

    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("com.squareup.okhttp3:okhttp:5.4.0")
                implementation("com.squareup.okio:okio:3.9.0")
                implementation(composeBom)
                implementation("androidx.compose.runtime:runtime")
                implementation("androidx.compose.foundation:foundation")
                implementation("androidx.compose.material3:material3")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.core:core-ktx:1.19.0")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
                implementation("androidx.activity:activity-compose:1.13.0")
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.ui:ui-graphics")
                implementation("androidx.compose.ui:ui-tooling-preview")
                implementation("androidx.compose.material:material-icons-extended")
            }
        }
        val iosMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation(composeBom)
                implementation("androidx.compose.runtime:runtime")
                implementation("androidx.compose.foundation:foundation")
                implementation("androidx.compose.material3:material3")
            }
        }
    }
}

cocoapods {
    summary = "MetaHelper Shared Library"
    homepage = "https://github.com/Builder106/meta-helper"
    version = "1.0"
    ios.deploymentTarget = "16.0"

    pod("mwdat-ios", :git => "https://github.com/facebook/meta-wearables-dat-ios.git", :tag => "0.7.0")
}

android {
    namespace = "com.metahelper.shared"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}