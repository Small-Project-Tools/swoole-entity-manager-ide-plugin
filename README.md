# Small Entity Manager IDE Core

![Small Entity Manager PhpStorm plugin](phpstorm/assets/plugin-logo.png)

Static, read-only project metadata for **Small Swoole Entity Manager Core 3** and the compatible **Symfony Bundle 2**. This repository contains the common engine shared by the VS Code and PhpStorm integrations. The **PhpStorm plugin** in [phpstorm/](phpstorm/) now indexes PHP natively without requiring a local interpreter; the PHP CLI here is retained for other IDE adapters. The [VS Code adapter](vscode/README.md) is available as a first 0.1.0 implementation; it uses the shared PHP CLI indexer and currently requires PHP 8.4+ in the VS Code extension host.

## Features

- Index PHP entities annotated with `#[OrmEntity]` and their `#[Field]`, `#[PrimaryKey]`, `#[ToOne]` and `#[ToMany]` properties.
- Resolve `#[Entity(YourEntity::class)]` and `#[Connection(...)]` on manager classes, including aliased imports and grouped `use` declarations.
- Include mapped fields from local traits and indexed ancestors.
- Follow query join aliases to their target managers and entities when completing fields/relations.
- Return `class-string`-based factory return-type hints and argument positions for query builders.
- Return deterministic JSON with a versioned schema and source locations. Does not execute, instantiate or autoload application classes and never connects to a database.

## VS Code integration (first 0.1.0 version)

The [VS Code extension](vscode/README.md) suggests Small manager classes, mapped fields, join relations and query aliases. Its `vscode_build` CI job runs Node tests and packages an installable VSIX artifact; no Marketplace release occurs on feature branches. Unlike the native PhpStorm plugin, this first adapter requires a PHP 8.4+ CLI for static indexing.

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

Install `small-entity-manager-phpstorm-<version>.zip` from the
`phpstorm_build` job artifact or the release asset. The automatically
generated GitLab `small-entity-manager-ide-core-<version>.zip` file is
**only a source archive**, not an installable PhpStorm plugin.

## Tagged releases

Pushing a tag creates a GitLab Release after the mandatory `quality → tests → build → deploy` checks.
The `deploy` job uploads two versioned, durable assets to the GitLab Generic Package Registry:
the shared PHP engine source archive and the installable PhpStorm plugin ZIP.
The `gitlab_release` job publishes both download links in **Deploy → Releases** through the Releases REST API using `CI_JOB_TOKEN` (no personal access token). A manual `repair_release` job can complete a previously failed tagged release without rebuilding archives.

Create a tag with `bin/release --dev|--patch|--minor|--major`, or push a tag yourself.
The version embedded in the PhpStorm plugin matches `CI_COMMIT_TAG` on tagged builds.
Branch builds use development version `1.0.0-RC3` until the next tag.

See [docs/releases.md](docs/releases.md).

## Copyright and license

Copyright 2026 Sébastien Kus. MIT License; see [LICENSE](LICENSE).
