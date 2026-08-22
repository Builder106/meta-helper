plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
}

group = "com.metahelper"
version = "1.0"

kotlin {
    iosArm64() {
        binaries.framework {
            baseName = "MetaHelperShared"
            isStatic = true
        }

        // Link iOS system frameworks - these are needed for the built-in platform libraries
        binaries.all {
            linkerOpts += listOf(
                "-framework", "Foundation",
                "-framework", "UIKit",
                "-framework", "Photos",
                "-framework", "AVFoundation",
                "-framework", "MediaPlayer"
            )
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        val iosArm64Main by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("io.ktor:ktor-client-darwin:3.0.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
                implementation("io.ktor:ktor-client-serialization:3.0.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn")
    }
}