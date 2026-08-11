pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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
                val token = localProperties.getProperty("github_token")
                           ?: providers.gradleProperty("github_token").orNull
                           ?: System.getenv("GITHUB_TOKEN")

                if (token == null || token.isEmpty()) {
                    println("ERROR: github_token NOT FOUND in local.properties")
                } else {
                    println("SUCCESS: github_token loaded (Starts with: ${token.take(4)}...)")
                }
                password = token
            }
        }
    }
}

rootProject.name = "MetaHelper-Android"

include(":app")
include(":shared")
project(":shared").projectDir = settingsDir.resolve("../shared")