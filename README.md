# Small Entity Manager IDE Core

**Canonical repository:** [Small-Project-Tools/swoole-entity-manager-ide-plugin](https://github.com/Small-Project-Tools/swoole-entity-manager-ide-plugin). The GitLab project remains available as the mirror source during CI validation.

![Small Entity Manager PhpStorm plugin](phpstorm/assets/plugin-logo.png)

Static, read-only project metadata for **Small Swoole Entity Manager Core 3** and the compatible **Symfony Bundle 2**. This repository contains the common engine shared by the VS Code and PhpStorm integrations. The **PhpStorm plugin** in [phpstorm/](phpstorm/) now indexes PHP natively without requiring a local interpreter; the PHP CLI here is retained for other IDE adapters. The [VS Code adapter](vscode/README.md) uses a native JavaScript indexer without requiring PHP, with a PHP 8.4+ CLI available as an optional indexing mode.

## Features

- Index PHP entities annotated with `#[OrmEntity]` and their `#[Field]`, `#[PrimaryKey]`, `#[ToOne]` and `#[ToMany]` properties.
- Resolve `#[Entity(YourEntity::class)]` and `#[Connection(...)]` on manager classes, including aliased imports and grouped `use` declarations.
- Include mapped fields from local traits and indexed ancestors.
- Follow query join aliases to their target managers and entities when completing fields/relations.
- Return `class-string`-based factory return-type hints and argument positions for query builders.
- Return deterministic JSON with a versioned schema and source locations. Does not execute, instantiate or autoload application classes and never connects to a database.

## VS Code integration

The [VS Code extension](vscode/README.md) suggests Small manager classes, mapped fields, join relations and query aliases. The `vscode-build` GitHub Actions job runs Node tests and produces a VSIX artifact; no Marketplace release occurs on feature branches. The default indexer runs in JavaScript without requiring a local PHP interpreter.

## PHP and installation

PHP **8.4+** with `json` and `tokenizer`. Runtime has no Composer dependencies. Install as a library or run its CLI from a checkout. In a project using Composer:

```sh
composer require --dev small/entity-manager-ide-core
vendor/bin/sem-ide index .
```

The `vendor/bin/` path assumes publication with `composer.json` `bin` metadata. With a checkout, run `php bin/sem-ide index /path/to/project`.

## Commands

```sh
php bin/sem-ide index /path/to/project > .sem-ide-index.json
php bin/sem-ide complete /path/to/project '{"kind":"field","manager":"App\\EntityManager\\UserManager","rootAlias":"user"}'
```

For joined fields:

```sh
php bin/sem-ide complete /path/to/project '{
  "kind": "field",
  "manager": "App\\EntityManager\\UserManager",
  "rootAlias": "user",
  "joins": [{"fromAlias":"user", "relation":"company", "alias":"owner"}],
  "alias": "owner"
}'
```

Completion `kind` is one of `manager`, `field`, `relation`, `alias`, or `parameter`. The IDE supplies the cursor's already-resolved manager and local join chain. `parameter` accepts `parameters` (string list) from the IDE's syntax analysis. This common engine does **not** yet parse entire query call chains or automatically infer local variable flow; that is the responsibility of the upcoming editor adapters.

## Stable JSON contract

See [docs/protocol.md](docs/protocol.md). `schemaVersion: 1` is the compatibility boundary; consumers must refuse versions they do not support. Keys in `entities` and `managers` are fully qualified PHP class names, without leading backslash. Source file names are relative to the requested project root.

## Development and tests

```sh
php bin/self-test                  # dependency-free functional smoke suite
composer install
vendor/bin/pest                   # Unit and Functional suites
vendor/bin/phpstan analyse       # max level
```

Or use Docker Compose:

```sh
docker compose up --build
```

Because this is a stateless **developer tool**, not a Small web application, no Symfony, Swoole, database, authentication or HTTP runtime is required; adding them would complicate static analysis and could execute user code. `bin/console` is not applicable. The Docker Compose service executes the test command instead of starting an HTTP server.

## Known limits (v0.1 foundation)

- Analyzes PHP source in the given root, but deliberately excludes `vendor`, caches and node_modules. IDE adapters must choose the workspace root; only workspace-local classes are indexed.
- Handles concrete `::class` expressions and string literal connection/database names; dynamic attribute arguments cannot be safely resolved and are reported or left `null`.
- Requires semicolon namespace declarations and supports simple and grouped `use` imports. Complex trait adaptations, multi-property declarations and anonymous classes are not part of the first extraction contract.
- Output describes symbols and completion candidates, not expression-level type checking or PHP execution. Caller must know the cursor position and query alias chain.

## Installing the PhpStorm plugin

Install the `small-entity-manager-phpstorm-<version>.zip` from a successful
[GitHub Actions build](https://github.com/Small-Project-Tools/swoole-entity-manager-ide-plugin/actions) (artifact `phpstorm-plugin-and-test-results`)
or from a [GitHub Release](https://github.com/Small-Project-Tools/swoole-entity-manager-ide-plugin/releases). Do not install GitHub's automatic
source-code ZIP as a PhpStorm plugin: it does not contain the JetBrains descriptor.

## Tagged releases

On a new version tag, [GitHub Actions](https://github.com/Small-Project-Tools/swoole-entity-manager-ide-plugin/actions) checks the PHP core,
VS Code and PhpStorm adapters, then publishes the source archive, the installable
PhpStorm ZIP and the VS Code VSIX as [release assets](https://github.com/Small-Project-Tools/swoole-entity-manager-ide-plugin/releases) if all
jobs pass. Historical tags were transferred with their original Git SHA values.
They do not automatically gain GitHub Release assets merely by being mirrored.

GitLab's former release pipeline and package links remain documented for
historical reference in [docs/releases.md](docs/releases.md).

## Copyright and license

Copyright 2026 Sébastien Kus. MIT License; see [LICENSE](LICENSE).
