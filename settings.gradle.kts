plugins {
    // Lets Gradle download the daemon JVM (gradle/gradle-daemon-jvm.properties) and toolchains.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "gradle-banned-warnings-plugin"
include("plugin")
