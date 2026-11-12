#!/usr/bin/env bash
# Offline integration tests: Git objects only, no real GitLab/GitHub writes.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
export GIT_CONFIG_GLOBAL="$tmp/gitconfig"
export HOME="$tmp/home"
mkdir -p "$HOME" "$tmp/bin"
git config --global user.name 'Migration QA'
git config --global user.email 'qa@example.invalid'
git config --global init.defaultBranch main

src="$tmp/source"
src_bare="$tmp/source.git"
dst="$tmp/github-target.git"
git init -q "$src"
printf 'synthetic fixture\n' > "$src/README.md"
git -C "$src" add README.md
git -C "$src" commit -qm 'test: initial synthetic commit'
git -C "$src" branch release/qa
git -C "$src" tag -a '1.0.0-RC1' -m 'synthetic RC1'
git clone --quiet --bare "$src" "$src_bare"

# Git rewrites only the intended GitHub URL to an isolated local bare target.
github_url='https://github.com/Small-Project-Tools/small-entity-manager-ide-core.git'
git config --global url."file://$dst".insteadOf "$github_url"
export FAKE_GH_DEST="$dst"
cat > "$tmp/bin/gh" <<'GH'
#!/usr/bin/env bash
set -euo pipefail
case "$1 $2" in
  'auth status' | 'auth setup-git')
    exit 0
    ;;
  'repo view')
    if [[ -f "$FAKE_GH_DEST/HEAD" ]]; then
      printf 'PRIVATE\n'
      exit 0
    fi
    exit 1
    ;;
  'repo create')
    git init -q --bare "$FAKE_GH_DEST"
    exit 0
    ;;
  'repo edit')
    git --git-dir="$FAKE_GH_DEST" symbolic-ref HEAD refs/heads/main
    exit 0
    ;;
esac
printf 'Unexpected gh command: %s\n' "$*" >&2
exit 2
GH
chmod +x "$tmp/bin/gh"
export PATH="$tmp/bin:$PATH"
export GITLAB_SOURCE_URL="$src_bare"
export GITHUB_TARGET='Small-Project-Tools/small-entity-manager-ide-core'
export EXPECTED_SOURCE_MAIN_SHA
EXPECTED_SOURCE_MAIN_SHA="$(git -C "$src" rev-parse main)"

# Fresh migration: all branch and tag object IDs must survive.
bash "$repo_root/bin/migrate-to-github" > "$tmp/first.log"
grep -F 'MIGRATION VERIFIED:' "$tmp/first.log"
test "$(git --git-dir="$dst" rev-parse refs/heads/main)" = "$EXPECTED_SOURCE_MAIN_SHA"
test "$(git --git-dir="$dst" rev-parse refs/heads/release/qa)" = "$EXPECTED_SOURCE_MAIN_SHA"
test "$(git --git-dir="$dst" rev-parse refs/tags/1.0.0-RC1)" = "$(git -C "$src" rev-parse refs/tags/1.0.0-RC1)"

# Idempotence without force pushes or branch deletion.
bash "$repo_root/bin/migrate-to-github" > "$tmp/second.log"
grep -F 'MIGRATION VERIFIED:' "$tmp/second.log"

# Fail closed when GitHub main is not the same immutable commit.
git -C "$src" checkout -q --detach main
printf 'divergent\n' > "$src/README.md"
git -C "$src" commit -qam 'test: divergent synthetic commit'
git -C "$src" push -q --force "$dst" HEAD:main
if bash "$repo_root/bin/migrate-to-github" > "$tmp/divergent.log" 2>&1; then
    echo 'Divergent target was not rejected' >&2
    exit 1
fi
grep -F 'Target main differs' "$tmp/divergent.log"

# Revert GitHub target to matching main to exercise the client-IP guard.
git --git-dir="$dst" update-ref refs/heads/main "$EXPECTED_SOURCE_MAIN_SHA"
git -C "$src" checkout -q main
git -C "$src" checkout -qb client-fixture
mkdir -p "$src/client-private"
printf 'synthetic placeholder only\n' > "$src/client-private/fixture.txt"
git -C "$src" add client-private/fixture.txt
git -C "$src" commit -qm 'test: client-like path fixture'
git clone -q --mirror "$src" "$tmp/client-source.git"
export GITLAB_SOURCE_URL="$tmp/client-source.git"
if bash "$repo_root/bin/migrate-to-github" > "$tmp/client.log" 2>&1; then
    echo 'Client-owned path was not rejected' >&2
    exit 1
fi
grep -F 'Client-owned paths found' "$tmp/client.log"

printf 'PASS: fresh migration, idempotency, divergent target and client-path guard\n'
