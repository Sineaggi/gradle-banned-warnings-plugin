plugins {
    id("com.gradle.plugin-publish") version "2.1.1" apply false
}

tasks.wrapper {
    retries = 3
}
