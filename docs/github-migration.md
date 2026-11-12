# GitLab to GitHub migration: Small Entity Manager IDE

The **single source repository** contains the shared PHP core, the PhpStorm plugin and the VS Code extension. Keep this monorepo intact to preserve cross-plugin changes and release tags.

Target: **Small-Project-Tools/small-entity-manager-ide-core**.

## One-time prerequisites

1. Ensure you have permission to create a **private** repository in Small-Project-Tools named small-entity-manager-ide-core. The migration script creates it if missing. If it already exists, it must be private and its main branch must be empty or identical to GitLab.
2. Obtain short-lived GitLab read and GitHub write credentials through an authorized credential helper. Never put credentials in remote URLs, source files or CI logs.
3. Use an authenticated Git client to copy the complete Git object history.

## Copy branches, history and tags

The recommended, tested procedure is:

~~~sh
bash tests/Migration/migrate-to-github-smoke.sh
bash bin/migrate-to-github
~~~

The script refuses public repositories and divergent main branches, compares all
branch/tag SHA values, and stops on generic sensitive paths. Set
`FORBIDDEN_PATH_REGEX` for additional project-specific names at runtime;
do not commit confidential project names to this repository.

The manual equivalent is:


Run in a clean temporary workspace:

~~~sh
git clone --mirror https://git.small-project.dev/pub/ide/small-entity-manager-ide-core.git small-entity-manager-ide-core.git
cd small-entity-manager-ide-core.git
git remote add github https://github.com/Small-Project-Tools/small-entity-manager-ide-core.git
# Transfer only branches and tags, not GitLab's internal merge-request refs.
git push github 'refs/heads/*:refs/heads/*' 'refs/tags/*:refs/tags/*'
git ls-remote --heads --tags origin | sort > ../gitlab-heads-tags.txt
git ls-remote --heads --tags github | sort > ../github-heads-tags.txt
diff -u ../gitlab-heads-tags.txt ../github-heads-tags.txt
~~~

The two remote ref listings must have identical SHAs. Keep the GitLab project intact until this and the GitHub Actions verification both pass.

## GitHub Actions

.github/workflows/ci.yml runs PHP quality, PHPStan, Pest tests, the VS Code Node tests and VSIX packaging, plus PhpStorm Gradle PSI tests and installable ZIP verification. For a new version tag, after all checks pass, it creates a GitHub Release with the PHP core source tarball, PhpStorm ZIP and VS Code VSIX. GitHub's built-in GITHUB_TOKEN handles release creation; no long-lived publishing token is required.

Verify that the VSIX contains dist/native-worker.js and core/bin/sem-ide, and that the PhpStorm ZIP contains a nested plugin JAR with META-INF/plugin.xml. Tag builds must embed exactly the tag version in both plugins.

Git transfer alone does **not** migrate GitLab issues, merge requests, GitLab Releases or Generic Package Registry binary artifacts. Historical releases and any GitLab package metadata need a separate migration.

## After the GitHub repository exists

- Update vscode/package.json repository.url and the root README and docs/releases.md to GitHub.
- Point Packagist to the new repository if the library is registered.
- Update developer clones to use GitHub origin only after checking the refs and workflows. bin/release pushes to origin.
- Keep the old GitLab project available read-only, then archive it only after successful verification.
