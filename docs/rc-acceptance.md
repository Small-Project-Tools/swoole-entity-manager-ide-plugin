# PhpStorm 1.0.0-RC1 acceptance checklist

## Automated quality gates

- Kotlin/JUnit: run the complete synthetic PHP and metadata fixture suite, including dynamic graph tests, 240 generated managers, join chains, null/unknown mappings, cancellation and concurrency.
- Lexer: mask comments, PHP strings, heredoc and nowdoc while preserving source offsets. A commented query must not become an executable completion candidate.
- Performance: use the in-memory cache; ensure cold indexing is cancellable and subsequent lookups are stable.
- PHP core: pass copyright checks, static analysis at max strictness, unit tests and functional tests.
- Packaging: verify configuration and structure using the official PhpStorm SDK and validate the ZIP contains the contributor, type provider, indexer and audit classes.
- Release metadata: accept uppercase 1.0.0-RC1, reject malformed tags, and verify tagged builds use the tag as the plugin version.

## Read-only integration acceptance

A separate large PHP codebase can be audited **in place** on an authorized machine using the `dev.smallproject.ide.ProjectCoverageAudit` entrypoint and an installed JDK/PhpStorm runtime.

The audit must never upload, copy or commit external source files, class names, identifiers, snippets, fixtures, logs containing source or derivative application artifacts to this project. Only aggregate counts may be discussed privately with the project's authorized user.

Check the following aggregated diagnostics: indexed manager/entity/field counts, relation mapping integrity, executable-only method occurrences, resolved contexts, non-empty candidate lists, parser exceptions and indexing latency. Unsupported dynamic calls are allowed to produce no suggestions; incorrect inferred classes or aliases are not.

## Manual IDE acceptance before stable 1.0.0

Confirm in an actual PhpStorm installation: Ctrl+Space opens the field dropdown in `getFieldForCondition()`, fields for joined aliases appear correctly, `firstCondition()` returns valid `ConditionField` expressions, operator enum choices work, insertion quotes and named arguments are valid, and type inference does not pollute unrelated PHP methods. A successful CI build alone is not sufficient proof of these editor interactions.

Tagging the release candidate is separate from publishing the future stable 1.0.0 release.
