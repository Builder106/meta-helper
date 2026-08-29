plugins {
    id("org.jetbrains.kotlin.multiplatform")
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
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        iosArm64Main.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("io.ktor:ktor-client-darwin:3.5.2")
            implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
            implementation("io.ktor:ktor-client-serialization:3.5.2")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn")
    }
}