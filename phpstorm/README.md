# Small Entity Manager — PhpStorm plugin (initial integration)

A native IntelliJ Platform Kotlin plugin depending on the bundled PhpStorm PHP plugin.

Features in this first integration:
- Query field completion for `getFieldForCondition()`, `addOrderBy()`, and `addFieldToUpdate()`.
- Relation and alias suggestions for `join()`, `innerJoin()`, `leftJoin()`, and `rightJoin()`.
- Uses the shared `sem-ide complete` JSON protocol, no duplicate ORM indexer.
- Bounded CLI execution; does not execute application PHP classes.

Requirements: PhpStorm 2025.1+, JDK 21 for the build, `php` on PATH, and `vendor/bin/sem-ide` in the opened PHP project (install `small/entity-manager-ide-core` as a dev Composer dependency).

Build: `./gradlew buildPlugin` (or `gradle buildPlugin`). Artifact: `build/distributions/*.zip`.

Limitations: the first pass recognizes simple PHP assignments and literal query aliases. Complex fluent expressions, parameter extraction, contextual documentation, type inference for factory returns and PHP PSI navigation are not implemented yet. The plugin deliberately returns no candidates for unresolved contexts.
