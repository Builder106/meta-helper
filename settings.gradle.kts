pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
        id("com.android.application") version "9.3.2"
        id("com.android.kotlin.multiplatform.library") version "9.3.2"
    }
}

val localProperties = java.util.Properties()
val localPropertiesFile = settingsDir.resolve("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()

        maven {
            url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
            credentials {
                username = "unused"
                password = localProperties.getProperty("github_token")
                    ?: providers.gradleProperty("github_token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "MetaHelper"

include(":shared")
