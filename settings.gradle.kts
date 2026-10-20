pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Courtside"

include(":shared", ":server")

if (!providers.gradleProperty("serverOnly").isPresent) include(":client", ":organizer", ":referee")
