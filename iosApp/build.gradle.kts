plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
    kotlin("native.cocoapods") version "2.4.10"
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
}

group = "com.metahelper"
version = "1.0"

kotlin {
    val iosTarget = iosArm64("ios") {
        binaries.framework {
            baseName = "MetaHelperApp"
            isStatic = true
        }
    }

    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation(composeBom)
                implementation("androidx.compose.runtime:runtime")
                implementation("androidx.compose.foundation:foundation")
                implementation("androidx.compose.material3:material3")
            }
        }
        val iosMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(composeBom)
                implementation("androidx.compose.runtime:runtime")
                implementation("androidx.compose.foundation:foundation")
                implementation("androidx.compose.material3:material3")
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.ui:ui-graphics")
                implementation("androidx.compose.ui:ui-tooling-preview")
            }
        }
    }
}

cocoapods {
    summary = "MetaHelper iOS App"
    homepage = "https://github.com/Builder106/meta-helper"
    version = "1.0"
    ios.deploymentTarget = "16.0"

    pod("mwdat-ios", :git => "https://github.com/facebook/meta-wearables-dat-ios.git", :tag => "0.7.0")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}