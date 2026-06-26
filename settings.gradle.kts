pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // LiteRT-LM: com.google.ai.edge.litertlm:litertlm-android
        maven { url = uri("https://dl.google.com/dl/android/maven2") }
        // llmedge: io.github.aatricks:llmedge
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://maven.pkg.github.com/aatricks/llmedge") }
    }
}

rootProject.name = "BRPAssistant"
include(":app")
