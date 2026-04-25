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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "TaskManager"

include(":app")
include(":core-network")
include(":core-ui")
include(":data")
include(":domain")
include(":feature-tasks")
include(":feature-work")
include(":feature-attachments")
include(":feature-projects")
include(":feature-users")
include(":feature-search")
