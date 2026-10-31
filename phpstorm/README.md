# Small Entity Manager — PhpStorm plugin (0.1.0)

![Small Entity Manager PhpStorm plugin](assets/plugin-logo.png)

The plugin icon uses the supplied Small Entity Manager artwork; its SVG is bundled as `META-INF/pluginIcon.svg` for the JetBrains Plugins interface.

A native IntelliJ Platform plugin for PhpStorm 2025.1+.
The JAR is compiled against official, individually published JetBrains IntelliJ Platform 2025.1 API modules (build 251.23774.435), not a downloaded IDE installer.
The `plugin.xml` declares a mandatory dependency on `com.jetbrains.php`; the plugin is intended for PhpStorm and other IDEs with that plugin installed.

## Supported completions

- Query fields in `getFieldForCondition()`, `addOrderBy()` and `addFieldToUpdate()`.
- Relations and aliases in `join()`, `innerJoin()`, `leftJoin()` and `rightJoin()`.
- Previously referenced named placeholders in `setParameter()`.
- Basic manager resolution via `$factory->get(YourManager::class)`, import aliases and `@var` documentation.
- The PHP CLI `sem-ide complete` supplies candidate names using the shared metadata index.

## Requirements

- PhpStorm 2025.1 or newer, with its bundled PHP plugin enabled.
- PHP CLI on the PATH.
- No Composer installation of the engine is required: the ZIP bundles the offline `sem-ide` CLI. A project-local `vendor/bin/sem-ide` takes precedence.
- JDK 21 and Gradle 9.2.1 for building.

## Build and installation

```sh
cd phpstorm
gradle clean test buildPlugin --no-daemon
```

The installable artifact is `build/distributions/small-entity-manager-phpstorm-<version>.zip`. Tagged CI pipelines use the Git tag as the version; local and branch builds default to `0.1.0`.
In PhpStorm: **Settings → Plugins → gear icon → Install Plugin from Disk**, then select the ZIP.

The Gradle build downloads small official JetBrains API JARs and creates the standard IntelliJ plugin ZIP layout (`small-entity-manager/lib/*.jar`).
The plugin manifest is embedded in that JAR and includes the version and minimum IDE build.

## Validation

The Kotlin/JUnit tests and plugin ZIP build run on every GitLab commit.
The offline PHP indexing engine has separate Pest and PHPStan quality gates.
The plugin has not yet been tested by launching an actual PhpStorm instance or by JetBrains Plugin Verifier; these remain release requirements.

## Known limitations

- Parsing is conservative and text-based rather than full PHP PSI; complex fluent expressions may not resolve.
- No factory return-type provider, PHP PSI navigation, or hover documentation yet.
- The Kotlin adapter uses a short cache and synchronous CLI calls; future improvements should use background execution.
- When no manager or relationship is resolved, no speculative candidates are produced.

## Completion and method signatures

Use **Ctrl+Space** inside a supported string argument to open the completion dropdown.
The plugin now resolves named factory arguments (`managerClass:`), imported manager
classes, query builders stored in variables, and multiline fluent chains with joins.
It matches the full typed string prefix (including dots in projections).
Completions include entity fields, relation names, existing aliases, projections
and parameter placeholders. The PHP CLI is bundled in the plugin ZIP.

Relevant public Core 3 signatures:
- `createQueryBuilder(?string $alias = null): QueryBuilder`
- `leftJoin(string $fromAlias, string $relationAlias, ?string $alias = null): LeftJoinBuilder`
- `endJoin(): QueryBuilder`
- `getFieldForCondition(string $fieldName, ?string $entityAlias = null): ConditionField`
- `where(): Bracket`
- `firstCondition(string|int|QueryBuilder|ConditionField $var1, ConditionOperatorType $operator, ...): Bracket`

**Important:** `firstCondition(var1: 'person.deletedAt IS NULL')` is not an ORM
field expression and is missing the mandatory `operator` argument. Use
`$query->getFieldForCondition('deletedAt', 'person')` as `var1` and
`ConditionOperatorType::isNull` as `operator`. PhpStorm itself provides
parameter information for these method signatures; the plugin complements
it with entity-aware literal suggestions.

The resolver remains conservative; arbitrary dynamically computed manager
classes and dynamic relation aliases are not inferred.
