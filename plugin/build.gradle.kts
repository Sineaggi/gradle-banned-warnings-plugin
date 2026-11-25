plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish")
}

testing {
    suites {
        val test = named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit)
        }

        register<JvmTestSuite>("functionalTest") {
            useJUnitJupiter(libs.versions.junit)
            dependencies {
                implementation(gradleTestKit())
            }

            targets {
                configureEach {
                    testTask.configure {
                        shouldRunAfter(test)
                        // The sample builds resolve the plugin from a file repository rather than with
                        // GradleRunner.withPluginClasspath(): the injected classpath breaks the Kotlin Gradle
                        // plugin's build services under the configuration cache.
                        dependsOn("publishAllPublicationsToFunctionalTestRepository")
                        val repo = layout.buildDirectory.dir("functional-test-repo")
                        inputs.dir(repo).withPropertyName("functionalTestRepo")
                        systemProperty("bannedWarnings.test.pluginRepo", repo.get().asFile.absolutePath)
                        systemProperty("bannedWarnings.test.pluginVersion", project.version.toString())
                        systemProperty("bannedWarnings.test.kotlinVersion", libs.versions.kotlin.get())
                        // Optional extra Maven repository for the sample builds, e.g. a Maven Central mirror.
                        providers.gradleProperty("bannedWarnings.test.mavenMirror").orNull?.let {
                            systemProperty("bannedWarnings.test.mavenMirror", it)
                        }
                    }
                }
            }
        }
    }
}

gradlePlugin {
    website.set("https://github.com/Sineaggi/gradle-banned-warnings-plugin")
    vcsUrl.set("https://github.com/Sineaggi/gradle-banned-warnings-plugin")
    plugins {
        register("io.github.sineaggi.banned-warnings") {
            implementationClass = "io.github.sineaggi.gradle.bannedwarnings.BannedWarningsPlugin"
            displayName = "Gradle Banned Warnings plugin"
            description = "Settings plugin that fails the build when banned compiler or build script warnings reappear, with an optional baseline"
            tags.set(listOf("warnings", "deprecation", "lint", "kotlin", "javac"))
        }
    }
}

tasks.compileJava {
    // Gradle 9 itself requires Java 17.
    options.release = 17
}

publishing {
    repositories {
        maven {
            name = "functionalTest"
            url = uri(layout.buildDirectory.dir("functional-test-repo"))
        }
    }
}

tasks.check {
    dependsOn(testing.suites.named("functionalTest"))
}
