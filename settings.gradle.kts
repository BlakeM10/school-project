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
        // MPAndroidChart (coach dashboard / progress line charts) is published on JitPack
        maven { url = uri("https://www.jitpack.io") }
    }
}

rootProject.name = "court-vision"
include(":app")
