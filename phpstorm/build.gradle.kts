plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.smallproject"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        phpstorm("2025.1")
        bundledPlugin("com.jetbrains.php")
    }
    // All current tests target pure Kotlin code: no IDE test harness required.
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin { jvmToolchain(21) }

intellijPlatform {
    // No settings pages; launching a full headless PhpStorm wastes CI resources.
    buildSearchableOptions = false
    pluginConfiguration {
        id = "dev.smallproject.entitymanager"
        name = "Small Entity Manager"
        version = project.version.toString()
        ideaVersion { sinceBuild = "251" }
    }
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "384m"
    maxParallelForks = 1
}
