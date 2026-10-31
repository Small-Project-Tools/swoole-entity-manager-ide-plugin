plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}
group = "dev.smallproject"
version = "0.1.0"
repositories { mavenCentral(); intellijPlatform { defaultRepositories() } }
dependencies {
    intellijPlatform {
        phpstorm("2025.1")
        bundledPlugin("com.jetbrains.php")
        pluginVerifier()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation(kotlin("test"))
}
kotlin { jvmToolchain(21) }
intellijPlatform {
    pluginConfiguration {
        id = "dev.smallproject.entitymanager"
        name = "Small Entity Manager"
        version = project.version.toString()
        ideaVersion { sinceBuild = "251" }
    }
}
tasks.test { useJUnitPlatform() }