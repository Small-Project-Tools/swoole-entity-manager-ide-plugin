# GitLab releases

A version tag triggers the full pipeline: `quality → tests → build → deploy → release`.

The `deploy` job depends on **both** `build` (PHP source archive) and
`phpstorm_build` (tested plugin ZIP), and uploads their artifacts to the
GitLab Generic Package Registry. GitLab Release creation happens only after
both uploads succeed.

The `gitlab_release` job uses the official `glab` container image and the
GitLab CI `release:` keyword. It authenticates with `CI_JOB_TOKEN` and
attaches permanent package-registry links for both artifacts.

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

The plugin's embedded version is determined from `CI_COMMIT_TAG` for tag
pipelines. The release asset links point to the package registry rather than
temporary CI job artifacts, which expire after seven days.

If quality, tests, packaging, or either upload fails, the release job does not
run. The job also fails if GitLab rejects the release creation.
