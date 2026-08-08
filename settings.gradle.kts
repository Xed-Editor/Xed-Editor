rootProject.name = "Xed-Editor"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        maven("https://repo.eclipse.org/content/groups/releases/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        maven("https://repo.eclipse.org/content/groups/releases/")
    }
}

include(":app", ":core:main", ":core:components", ":core:resources")

include(":features:terminal", ":features:extensions", ":features:runner", ":features:git")

include(":baselineprofile", ":benchmark", ":benchmark2")
include(":features:terminal:proot")
include(":features:terminal:link2symlink")
include(":features:terminal:xed-cli")
