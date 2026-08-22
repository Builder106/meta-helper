plugins {
    id("org.jetbrains.kotlin.multiplatform")
    kotlin("native.cocoapods") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose")
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

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("androidx.compose:compose-bom:2026.06.01")
            implementation("androidx.compose.runtime:runtime")
            implementation("androidx.compose.foundation:foundation")
            implementation("androidx.compose.material3:material3")
        }
        getByName("iosMain") {
            dependencies {
                implementation(project(":shared"))
                implementation("androidx.compose:compose-bom:2026.06.01")
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}