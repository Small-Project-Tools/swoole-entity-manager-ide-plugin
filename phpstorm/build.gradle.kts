plugins { kotlin("jvm") version "2.3.20" }
group = "dev.smallproject"
version = providers.environmentVariable("CI_COMMIT_TAG").orElse("0.1.0").get().ifBlank { "0.1.0" }
repositories {
  mavenCentral()
  maven("https://www.jetbrains.com/intellij-repository/releases")
  maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}
dependencies {
  compileOnly("com.jetbrains.intellij.platform:lang:251.23774.435") { isTransitive = false }
  compileOnly("com.jetbrains.intellij.platform:core:251.23774.435") { isTransitive = false }
  compileOnly("com.jetbrains.intellij.platform:util:251.23774.435") { isTransitive = false }
  compileOnly("com.jetbrains.intellij.platform:extensions:251.23774.435") { isTransitive = false }
  compileOnly("com.jetbrains.intellij.platform:analysis:251.23774.435") { isTransitive = false }
  testImplementation(kotlin("test-junit5"))
  testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
kotlin { jvmToolchain(21) }
tasks.test { useJUnitPlatform() }
tasks.processResources {
    filesMatching("META-INF/plugin.xml") {
        expand(mapOf("pluginVersion" to project.version.toString()))
    }
}

tasks.register<Zip>("buildPlugin") {
    dependsOn(tasks.named("jar"))
    archiveFileName = "small-entity-manager-phpstorm-" + project.version + ".zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    from(tasks.named("jar")) { into("small-entity-manager/lib") }
    // The offline CLI has no Composer runtime dependencies: bundle it with the IDE plugin.
    from(rootProject.file("../src")) { into("small-entity-manager/core/src") }
    from(rootProject.file("../bin/sem-ide")) { into("small-entity-manager/core/bin") }
}
