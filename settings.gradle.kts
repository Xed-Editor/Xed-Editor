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
        maven { url = uri("https://jitpack.io")}
        maven("https://oss.sonatype.org/content/repositories/snapshots/")

    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io")}
        maven("https://oss.sonatype.org/content/repositories/snapshots/")

    }
}

rootProject.name = "Xed Editor"
include(":app")
include(":libsettings")
include(":libPlugin")
include(":libRunner")
include(":libEditor")
include(":commons")
include(":FileTree")
