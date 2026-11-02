import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.smallproject"
version = providers.environmentVariable("CI_COMMIT_TAG").orElse("1.0.0-RC4").get().ifBlank { "1.0.0-RC4" }

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        // Official PhpStorm SDK and bundled PHP PSI / PhpTypeProvider4 API.
        phpstorm("2025.1.3")
        bundledPlugin("com.jetbrains.php")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation("junit:junit:4.13.2") // Required by JetBrains test bootstrap.
}

kotlin { jvmToolchain(21) }

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion { sinceBuild = "251" }
    }
}

tasks.test { useJUnitPlatform() }

tasks.named<Zip>("buildPlugin") {
    archiveFileName = "small-entity-manager-phpstorm-" + project.version + ".zip"
}
