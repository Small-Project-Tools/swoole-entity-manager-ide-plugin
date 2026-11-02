# Small Entity Manager — PhpStorm plugin

![Small Entity Manager PhpStorm plugin](assets/plugin-logo.png)

Native PhpStorm 2025.1+ plugin for Small Swoole Entity Manager Core 3 and its Symfony Bundle. Unlike the shared PHP CLI engine, **the PhpStorm plugin does not require PHP, Composer, a configured PHP interpreter or a running application**. It reads source files statically in the background and does not execute application PHP.

## Features

- Completion dropdown inside query string arguments: mapped entity fields, relations, join aliases, scalar projections, named parameters.
- At `firstCondition()`, `andCondition()`, `orCondition()` and `xorCondition()`, suggest actual `ConditionField` expressions such as `$query->getFieldForCondition('email')`; after the comma, suggest `ConditionOperatorType` enum cases. The dropdown also opens when typing `(` or `,` inside these calls.
- Supports a stored `$query` or a simple inline `createQueryBuilder()` call. Inline expressions only suggest root entity fields, not joins from another builder instance.
- Completion of manager-class references in a factory call: start typing \`$factory->get(Us\` and press **Ctrl+Space** to insert a qualified \`Manager::class\`.
- Type inference through PhpStorm's **PhpTypeProvider4** for manager factory `get(UserManager::class)`; methods with declared PHP return types retain PhpStorm's native inference.
- Imported and aliased class names, grouped imports, local traits, ancestor entity properties, fluent calls, named arguments and stored query builders.
- Factory-created managers, typed method parameters, and managers injected as typed properties or promoted readonly constructor properties (`$this->manager`).
- Native bounded indexing: skips vendor, node_modules, build outputs and generated directories, limits files to 2 MiB and 10,000 source files, preloads metadata and serves completion from an in-memory snapshot, with a cancellable initial scan.
- No PHP process, shell command, Composer autoload or SQL connection is invoked.

## Installation

In PhpStorm: **Settings → Plugins → gear icon → Install Plugin from Disk**.
Install **`small-entity-manager-phpstorm-1.0.0-RC6.zip`** from the GitLab
`phpstorm_build` job artifact. **Do not install
`small-entity-manager-ide-core-1.0.0-RC6.zip`**: GitLab generates that as
a source archive, and it has no JetBrains `META-INF/plugin.xml` descriptor.


No PHP installation on Windows is required for these completion and inference features. Normal PhpStorm PHP debugging, PHPUnit and code execution still need a configured interpreter as usual.

## Build

\`\`\`sh
cd phpstorm
gradle --no-daemon test buildPlugin
\`\`\`

Uses Gradle 9.2.1, JDK 21 and the official PhpStorm 2025.1.3 SDK with its bundled PHP plugin. JetBrains' runtime PHP PSI APIs are provided by PhpStorm and are **not** packaged inside our plugin.

Output: \`build/distributions/small-entity-manager-phpstorm-<version>.zip\`.

Tagged builds take their version from \`CI_COMMIT_TAG\`. The branch development version is \`1.0.0-RC6\`.

## Query example

\`\`\`php
use Demo\EntityManager\UserManager;
use Small\SwooleEntityManager\QueryBuilder\RelationalQueryBuilder\Enum\ConditionOperatorType;

/** @var \Small\SwooleEntityManager\Factory\EntityManagerFactory $factory */
$manager = $factory->get(UserManager::class);
$query = $manager->createQueryBuilder('user');
$query->leftJoin('user', 'company', 'owner')->endJoin();
$query->getFieldForCondition(''); // Ctrl+Space: id, email, companyId, createdAt
$query->getFieldForCondition('name', 'owner');
$query->where()->firstCondition(
    $query->getFieldForCondition('email'),
    ConditionOperatorType::equal,
    ':email',
);
\`\`\`

**Signatures matter:** Core 3 factory uses \`get(string $manager, bool $new = false)\` whereas Symfony Bundle factory uses \`get(string $managerClass, bool $new = false)\`. Prefer positional \`get(UserManager::class)\` if the same source is intended for both. The plugin understands \`manager:\` and \`managerClass:\`, but it does not change those PHP signatures.

For conditions, the correct first argument to \`firstCondition()\` may be a \`ConditionField\` from \`getFieldForCondition()\`; the second argument is a \`ConditionOperatorType\` value. The plugin does not inject false field names directly into \`firstCondition()\` string arguments.

## Tests / stability

Kotlin and dynamically generated stress tests cover native indexing, manager resolution (including `$this` inside a manager), join aliases, insertion syntax, type inference, cancellation, concurrency, parser fuzzing, and avoidance of unrelated factory calls. GitLab builds the plugin with the **real** JetBrains PHP API.

The first completion indexes source directly on the cancellable IDE completion thread if preloading is not finished. All later completions use the memory snapshot immediately. Automatic popup can be disabled in PhpStorm settings; Ctrl+Space triggers manual completion.

This is conservative static source analysis. Files with unsupported dynamic attributes or runtime-computed class names may not yield completions; completion results are refreshed after about 30 seconds.

## Deployment status

Passing Kotlin tests and successful plugin packaging verify bytecode compilation against the official SDK; they **do not** replace a runtime smoke test on an actual Windows PhpStorm instance. Test installation and real-editor completion before publishing a stable release.

## Diagnose missing completions

In PhpStorm use **Tools → Small Entity Manager: Diagnose Completion** while the caret is inside `getFieldForCondition()` or `firstCondition()`. The local diagnostics show the number of indexed managers and entities, the resolved query and alias, and the number of completion candidates; no source code is sent externally.

### Empty arguments

Completion now opens directly after `getFieldForCondition(`, even if no string quote has been typed yet. Choosing a property inserts the complete quoted PHP literal. For a joined property the plugin inserts separate `fieldName` and `entityAlias` arguments, preserving named arguments when they are used.

### Completion popup reliability

The native index is preloaded when the project opens. The first completion also loads
the index synchronously on IntelliJ's cancellable completion thread if startup indexing
has not finished. The dropdown no longer depends on a second asynchronous completion
popup after an empty lookup. Subsequent completions use an immutable memory snapshot;
stale snapshots remain available while the index refreshes in the background.

Completion auto-popup is registered via TypedHandlerDelegate.checkAutoPopup.
No PHP executable or Composer installation is needed for native completion.

## Release candidate acceptance and external-project validation

The RC acceptance suite covers dynamically generated synthetic metadata,
large graph navigation, self-manager query builders and cancellation.
Completion should also be verified in a running IDE after restart.
The read-only coverage audit can be run against a local external project
without exporting source or data.

### RC verification

A deterministic Kotlin test suite uses synthetic managers, malformed and partial PHP, joins, inherited metadata, concurrency, cancellation, lexical masking, and release-tag validation. The standalone `ProjectCoverageAudit` main entrypoint can inspect a separate PHP project locally and prints aggregated counts only. Never add external project sources or derived fixtures to this repository.
