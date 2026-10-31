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
        // Only shared IntelliJ Platform APIs are used by this extension.
        // The PHP plugin is required at runtime via plugin.xml.
        // Multi-OS archive excludes the bundled JetBrains Runtime and is
        // smaller to unpack in disk-constrained GitLab runners.
        intellijIdeaCommunity("2025.1") { useInstaller = false }
    }
    // All current tests target pure Kotlin code: no IDE test harness required.
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
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
