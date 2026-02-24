// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.9.6" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
