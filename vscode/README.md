# Small Entity Manager — VS Code extension (0.4.0)

First VS Code adapter for **Small Swoole Entity Manager Core 3** and its Symfony Bundle.
Uses a JavaScript/PHP-parser AST indexer by default, and supports the shared PHP CLI indexer as an optional fallback. PHP source is parsed, not executed.
No database connection, Composer runtime or application bootstrap is needed.

## Requirements and installation

- VS Code 1.95+ on desktop or remote extension host.
- **No PHP required** in default native indexing mode. PHP 8.4+ is optional when switching to the shared PHP CLI indexer (`smallEntityManager.indexingMode: php`).
- Install the `small-entity-manager-vscode-0.4.0.vsix` artifact with **Extensions → ... → Install from VSIX**.
- If PHP is not on PATH, set `smallEntityManager.phpExecutable` to an absolute path.
- Open the root of the PHP project as a VS Code folder. In multi-root workspaces, each folder is indexed independently.

## Features

- Autocompletion of Small manager classes in typed `EntityManagerFactory::get(...)` calls.
- Autocompletion of mapped fields in `$query->getFieldForCondition(...)`, `addOrderBy(...)`, `select(...)`, `addSelect(...)`.
- Root alias and mapped relation completion in `leftJoin(...)`, `innerJoin(...)`, `rightJoin(...)`, `join(...)`.
- Known joined aliases suggested as the second argument of field methods.
- Detection of `$manager = $factory->get(UserManager::class)` and `$query = $manager->createQueryBuilder('user')` with aliased `use` imports and basic typed manager variables.
- Cached, per-workspace metadata with automatic invalidation on PHP source edits, plus **Small Entity Manager: Refresh Index** and **Small Entity Manager: Diagnose Completion** commands.

```php
use Demo\EntityManager\UserManager;
use Small\SwooleEntityManager\Factory\EntityManagerFactory;

function demo(EntityManagerFactory $factory): void {
    $manager = $factory->get(UserManager::class);
    $query = $manager->createQueryBuilder('user');
    $query->getFieldForCondition(''); // mapped fields, Ctrl+Space
    $query->leftJoin('user', '', 'company'); // mapped relations
    $query->getFieldForCondition('name', ''); // query aliases
}
```

### Advanced completion (0.4.0)

- Condition expressions for `firstCondition`, `andCondition`, `orCondition` and `xorCondition` with mapped root and joined fields.
- All 15 `ConditionOperatorType` cases from the Small core, with fully qualified insertion and enum-case suffix completion.
- Named `setParameter()` placeholder completion; aggregated fields (`count`, `sum`, `avg`, `min`, `max`) and `addFieldToUpdate()`.
- Completion of public methods on typed local managers, including workspace-specific, inherited and trait methods (static AST scan; protected/private and static methods are excluded).
- Grouped PHP imports, named argument positions, promoted manager properties and explicit joined aliases.
- The native JavaScript indexer supplies entity, manager and public-method metadata by default. The read-only PHP CLI remains an explicitly selected fallback.

### Context resolution and navigation (0.4.0)

- Recognize managers returned by typed controller accessors, PHPDoc `@var` declarations, and promoted typed properties. Accessors with abstract return types resolve local delegated calls only when all supported return branches identify the same mapped manager.
- Support fields on simple inline builders created by createQueryBuilder and from typed controller accessors.
- Track an inline join in a query assignment and handle the default entity alias when createQueryBuilder has no arguments.
- Resolve `$this->createQueryBuilder()` inside the matching indexed manager class, without inferring a manager from unrelated classes or namespaces.
- Complete fields for stored and inline `createDeleteBuilder()` expressions.
- Require imported factory aliases to resolve to a known Small factory, not an unrelated class with the same short name.
- Provide Go to Definition (F12) on mapped manager classes, fields and relations. All targets are checked to stay within the current workspace, including symlink resolution.
- Avoid offering Small ORM fields after unrelated factory calls.

### Native indexing (0.4.0)

The native scanner uses `php-parser` to extract ORM attributes, manager/entity mapping, inherited/trait fields, relations, imports and namespace resolution. Indexing executes inside a Node worker, not on the VS Code extension-host event loop. It never runs application PHP, Composer, or SQL. Parser limitations may affect unsupported or future PHP syntax; switch to `indexingMode: php` for the legacy indexer when necessary.

### Scope and limitations

The quoted shorthand `getFieldForCondition('owner.')` offers joined fields and inserts the supported two-argument form `getFieldForCondition('name', 'owner')`.

This is a conservative static adapter, not PhpStorm PHP PSI. It now supports typed controller accessors, simple fluent inline builders and local inherited manager methods, but does not resolve arbitrary expression chains, all nested factory calls, or runtime-computed manager classes. It does not inject a native type provider into a separate PHP language server. Existing PHP language server integration is not modified. It does not suggest symbols for unresolved managers, recursive or ambiguous accessors, and does not inject Small proposals into unrelated factory calls. The parser does not fully understand PHP heredoc syntax.

The first completion may wait for static indexing; later suggestions use the cached snapshot. Both indexers exclude `vendor` and common generated directories and never execute project code. Only workspace files are indexed. Index diagnostics are shown locally, never uploaded.

## Build and verify

At the monorepo root, run:

```sh
cd vscode
npm install --no-audit --no-fund
npm test
npm run bundle-core
npx vsce package --no-dependencies
```

The generated VSIX **must** contain `extension/dist/native-worker.js`, `extension/core/bin/sem-ide`, `extension/core/src/Autoload.php` and `extension/src/extension.js`. The GitLab job `vscode_build` checks and publishes this package as a CI artifact. No tag or marketplace publication is performed by the initial feature branch.

Copyright 2026 Sébastien Kus. MIT License.
