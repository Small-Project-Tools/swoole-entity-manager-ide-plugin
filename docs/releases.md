# Releases: GitHub primary, GitLab historical

## New GitHub releases

Canonical repository: https://github.com/Small-Project-Tools/swoole-entity-manager-ide-plugin

A new version tag triggers `.github/workflows/ci.yml` (PHP quality and tests,
VS Code native indexer tests and VSIX build, PhpStorm Kotlin/PSI tests and ZIP build).
Only if all jobs succeed does the `release` job publish three assets:

- `small-entity-manager-ide-core-<tag>.tar.gz`
- `small-entity-manager-phpstorm-<tag>.zip`
- `small-entity-manager-vscode-<tag>.vsix`

The GitHub workflow uses the built-in `GITHUB_TOKEN`. An existing tag mirrored
from GitLab is not by itself a new GitHub Release; historical release binary
assets must be migrated separately if required.

## Historical GitLab release process


A version tag triggers the full pipeline: `quality → tests → build → deploy → release`.

The `deploy` job depends on **three** build jobs: `build` (PHP source archive),
`phpstorm_build` (tested plugin ZIP), and `vscode_build` (tested VSIX).
It uploads all three artifacts to the GitLab Generic Package Registry. GitLab Release creation happens only after
all three uploads succeed.

The `gitlab_release` job uses the GitLab Releases REST API directly.
It authenticates with `CI_JOB_TOKEN` using the `JOB-TOKEN` header, without
requiring `glab`, a personal access token, or the GitLab CI `release:` keyword.
The command is idempotent: it creates a missing release or updates an existing
one and reconciles both package-registry links. It verifies that all three links
appear in the resulting release.

## Tag creation

```sh
bin/release --dev
bin/release --patch
bin/release --minor
bin/release --major
```

The project uses SemVer tags such as `0.1.0` and `0.1.1-dev-1`.
Manual tag pushes trigger the same release pipeline. Creating a tag is an
explicit release action; committing this CI change does **not** publish a release.

## Assets

For tag `X.Y.Z`:

- `small-entity-manager-ide-core-X.Y.Z.tar.gz` — PHP metadata engine sources.
- `small-entity-manager-phpstorm-X.Y.Z.zip` — installable PhpStorm plugin.
- `small-entity-manager-vscode-X.Y.Z.vsix` — installable VS Code extension.

Both plugins use `CI_COMMIT_TAG` as their embedded version in tag pipelines.
The VS Code branch-build package keeps its development version from `vscode/package.json`. The release asset links point to the package registry rather than
temporary CI job artifacts, which expire after seven days.

If quality, tests, packaging, or any upload fails, the release job does not
run. The job also fails if GitLab rejects the release creation.

## Repair a failed release for an existing tag

Both `0.1.0` and `0.2.0` completed their package deployments, but the
former GitLab CI `release:` job failed. **Do not move or recreate these tags.**

Run **CI/CD → Pipelines → New pipeline** on the **main** branch, and
supply `RELEASE_TAG=0.2.0` (or `0.1.0`) as a variable.
Then run the manual `repair_release` job. The job updates only GitLab
Release metadata and asset links; existing package files remain unchanged.
The release API checks the release and confirms all three links at the end.

Manual repair has an explicit `RELEASE_TAG` guard and is unavailable from
tag pipelines. All new version tags use `gitlab_release` automatically.

## Release candidate 1.0.0-RC1

Use the exact uppercase tag 1.0.0-RC1, without a v prefix.
Once the acceptance suite passes, create an annotated tag:

    git tag -a 1.0.0-RC1 -m "release: 1.0.0-RC1"
    git push origin 1.0.0-RC1

Tagging triggers quality, unit and functional tests, the Gradle SDK build,
both package uploads and GitLab release creation.

Do not tag before validation on a large independent project.
Audit external projects strictly in place: never transmit their source
or generated fixtures to the Small Project GitLab repository.
