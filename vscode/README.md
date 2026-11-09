# Small Entity Manager — VS Code extension (0.1.0)

First VS Code adapter for **Small Swoole Entity Manager Core 3** and its Symfony Bundle.
Uses the shared PHP static metadata indexer: PHP source is parsed, not executed.
No database connection, Composer runtime or application bootstrap is needed.

## Requirements and installation

- VS Code 1.95+ on desktop or remote extension host.
- **PHP 8.4+** on the machine running the VS Code extension host, including `json` and `tokenizer` (the VS Code adapter does **not** yet embed a native JavaScript PHP indexer).
- Install the `small-entity-manager-vscode-0.1.0.vsix` artifact with **Extensions → ... → Install from VSIX**.
- If PHP is not on PATH, set `smallEntityManager.phpExecutable` to an absolute path.
- Open the root of the PHP project as a VS Code folder. In multi-root workspaces, each folder is indexed independently.

## Initial features

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

### Scope and limitations

The quoted shorthand `getFieldForCondition('owner.')` offers joined fields and inserts the supported two-argument form `getFieldForCondition('name', 'owner')`.

This is a deliberately conservative 0.1 adapter using static string/assignment recognition, not the full PHP PSI of the PhpStorm plugin. It does not yet support arbitrary expression chains, grouped imports, nested factory calls, semantic type providers, automatic insertions of `ConditionField` expressions in `firstCondition()`, or runtime-computed manager classes. It does not suggest symbols for unresolved managers, and does not inject Small proposals into unrelated factory calls. The parser does not fully understand PHP heredoc syntax.

The first completion may wait for static indexing; later suggestions use the cached snapshot. The indexer excludes `vendor` and common generated directories and never executes project code. Only workspace files are indexed. Index diagnostics are shown locally, never uploaded.

## Build and verify

At the monorepo root, run:

```sh
cd vscode
npm install --no-audit --no-fund
npm test
npm run bundle-core
npx vsce package --no-dependencies
```

The generated VSIX **must** contain `extension/core/bin/sem-ide`, `extension/core/src/Autoload.php` and `extension/src/extension.js`. The GitLab job `vscode_build` checks and publishes this package as a CI artifact. No tag or marketplace publication is performed by the initial feature branch.

Copyright 2026 Sébastien Kus. MIT License.
