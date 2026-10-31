# Small Entity Manager — PhpStorm plugin (initial integration)

A native IntelliJ Platform Kotlin plugin requiring the PHP plugin at runtime. It compiles against IntelliJ IDEA Community 2025.1 because this implementation only references shared IntelliJ Platform APIs. The `plugin.xml` retains `<depends>com.jetbrains.php</depends>`.

Features in this first integration:
- Query field completion for `getFieldForCondition()`, `addOrderBy()`, and `addFieldToUpdate()`.
- Relation and alias suggestions for `join()`, `innerJoin()`, `leftJoin()`, and `rightJoin()`.
- Uses the shared `sem-ide complete` JSON protocol, no duplicate ORM indexer.
- Bounded CLI execution; does not execute application PHP classes.

Requirements: PhpStorm 2025.1+, JDK 21 for the build, `php` on PATH, and `vendor/bin/sem-ide` in the opened PHP project (install `small/entity-manager-ide-core` as a dev Composer dependency).

Build: `./gradlew buildPlugin` (or `gradle buildPlugin`). Artifact: `build/distributions/*.zip`.

Limitations: conservative text-based analysis (not full PHP PSI). Complex fluent expressions, contextual hover documentation, factory return-type inference and PSI navigation are not yet implemented. Named parameter completions are derived from previously seen placeholders. The plugin deliberately returns no candidates for unresolved contexts.

The CLI integration now uses a testable SemIdeClient and a 3-second cache for repeated requests. The CI job phpstorm_build compiles and packages the plugin on every commit, and publishes its ZIP as a build artifact when successful.

## CI resource management

The Gradle build uses versions compatible with Gradle 9.2.1, limits worker
concurrency, compiles Kotlin inside the bounded Gradle JVM, and disables the
headless IDE searchable-options task because this plugin does not register any
settings pages. Unit tests run with JUnit 5 without the large IntelliJ test
framework. GitLab serializes heavy PhpStorm builds and retries one runner system
failure; actual compiler/test failures are never retried or ignored.

The IntelliJ Community build target reduces SDK size but does not independently verify PhpStorm runtime compatibility. Run Plugin Verifier or install in PhpStorm before publishing a release.

The compile target uses JetBrains' multi-OS IDEA Community archive (`useInstaller = false`), which omits the bundled JBR and reduces extraction pressure. This is intended for compilation and ZIP packaging, not running the IDE or validating the plugin inside PhpStorm.
