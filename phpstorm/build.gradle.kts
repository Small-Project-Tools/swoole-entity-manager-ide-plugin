import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.smallproject"
version = providers.environmentVariable("CI_COMMIT_TAG").orElse("1.0.0-RC6").get().ifBlank { "1.0.0-RC6" }

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        // Default CI SDK. A local IDE can be selected for cross-version PSI tests.
        val localIdePath = providers.environmentVariable("SMALL_PHPSTORM_IDE_HOME")
            .orNull?.takeIf { it.isNotBlank() }
        if (localIdePath != null) {
            local(localIdePath)
        } else {
            phpstorm("2025.1.3")
        }
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

tasks.test {
    useJUnitPlatform()

    // JetBrains test fixtures may retain non-daemon background workers. A
    // bounded number of test classes per JVM prevents one leaked worker from
    // keeping the entire CI suite alive after the tests have completed.
    maxParallelForks = 1
    forkEvery = 5
    // Leave native memory headroom for the Gradle daemon under the 2 GiB CI limit.
    maxHeapSize = "448m"
    jvmArgs("-XX:MaxMetaspaceSize=320m", "-XX:ActiveProcessorCount=2")
    systemProperty("junit.jupiter.execution.timeout.test.method.default", "180s")

    testLogging {
        // Explicit test names make a blocked CI job diagnosable. Logs are kept
        // even when the Gradle job ultimately times out.
        events("started", "failed")
    }
}

tasks.named<Zip>("buildPlugin") {
    archiveFileName = "small-entity-manager-phpstorm-" + project.version + ".zip"
}
