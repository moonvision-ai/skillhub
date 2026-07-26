#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<EOF
Usage: [SSH_USER=user] [YES=1] $0 <TAG> [BRANCH]

Build and push SkillHub server and web images on the remote build host.

Examples:
  SSH_USER=sam $0 v0.2.13 feature/configurable-auth-entry-policy
  SSH_USER=sam YES=1 $0 v0.2.13
EOF
}

die() {
  printf 'Error: %s\n' "$1" >&2
  exit 2
}

if (( $# < 1 || $# > 2 )); then
  usage >&2
  exit 2
fi

TAG="$1"
BRANCH="${2:-}"
SSH_USER="${SSH_USER:-root}"

if [[ ! "$TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
  die "Invalid TAG: $TAG"
fi

if [[ ! "$SSH_USER" =~ ^[A-Za-z_][A-Za-z0-9_.-]*[$]?$ ]]; then
  die "Invalid SSH_USER: $SSH_USER"
fi

command -v git >/dev/null 2>&1 || die 'git is required'
command -v ssh >/dev/null 2>&1 || die 'ssh is required'

if [[ -z "$BRANCH" ]]; then
  BRANCH="$(git branch --show-current)"
  [[ -n "$BRANCH" ]] || die 'Cannot determine BRANCH from detached HEAD; pass it explicitly'
fi

if ! git check-ref-format --branch "$BRANCH" >/dev/null 2>&1; then
  die "Invalid BRANCH: $BRANCH"
fi

JUMP_HOST="${SSH_USER}@59.110.17.213"
BUILD_HOST="${SSH_USER}@172.17.20.220"
SERVER_IMAGE="registry.cn-beijing.aliyuncs.com/moonvision/skillhub-server:${TAG}"
WEB_IMAGE="registry.cn-beijing.aliyuncs.com/moonvision/skillhub-web:${TAG}"

cat <<EOF
========================================
  Branch:       $BRANCH
  Jump host:    $JUMP_HOST
  Build host:   $BUILD_HOST
  Server image: $SERVER_IMAGE
  Web image:    $WEB_IMAGE
  Platform:     linux/amd64
========================================
EOF

if [[ "${YES:-0}" != '1' ]]; then
  printf 'Build and push these images? [y/N]: '
  read -r confirm
  case "$confirm" in
    y|Y|yes|YES) ;;
    *)
      printf 'Cancelled.\n'
      exit 0
      ;;
  esac
fi

printf -v remote_command 'bash -s -- %q %q' "$TAG" "$BRANCH"
start_time="$(date +%s)"

ssh -J "$JUMP_HOST" "$BUILD_HOST" "$remote_command" <<'REMOTE_BUILD_SCRIPT'
set -euo pipefail

TAG="$1"
BRANCH="$2"
REPO_URL='git@github.com:moonvision-ai/skillhub.git'
REPO_DIR="$HOME/skillhub"
SERVER_REPOSITORY='registry.cn-beijing.aliyuncs.com/moonvision/skillhub-server'
WEB_REPOSITORY='registry.cn-beijing.aliyuncs.com/moonvision/skillhub-web'
SERVER_IMAGE="${SERVER_REPOSITORY}:${TAG}"
WEB_IMAGE="${WEB_REPOSITORY}:${TAG}"

command -v git >/dev/null 2>&1 || {
  printf 'Error: git is required on the build host\n' >&2
  exit 2
}
command -v docker >/dev/null 2>&1 || {
  printf 'Error: docker is required on the build host\n' >&2
  exit 2
}
docker buildx version >/dev/null 2>&1 || {
  printf 'Error: Docker Buildx is required on the build host\n' >&2
  exit 2
}

if [[ -e "$REPO_DIR" && ! -d "$REPO_DIR/.git" ]]; then
  printf 'Error: %s exists but is not a Git repository\n' "$REPO_DIR" >&2
  exit 2
fi

if [[ ! -d "$REPO_DIR/.git" ]]; then
  printf '\n[1/4] Cloning SkillHub...\n'
  git clone "$REPO_URL" "$REPO_DIR"
fi

origin_url="$(git -C "$REPO_DIR" remote get-url origin)"
if [[ "$origin_url" != "$REPO_URL" ]]; then
  printf 'Error: unexpected origin URL in %s: %s\n' "$REPO_DIR" "$origin_url" >&2
  exit 2
fi

if [[ -n "$(git -C "$REPO_DIR" status --porcelain)" ]]; then
  printf 'Error: %s has uncommitted changes; clean them before building\n' "$REPO_DIR" >&2
  exit 2
fi

printf '\n[1/4] Fetching branch %s...\n' "$BRANCH"
git -C "$REPO_DIR" fetch origin --prune
if ! git -C "$REPO_DIR" show-ref --verify --quiet "refs/remotes/origin/$BRANCH"; then
  printf 'Error: remote branch origin/%s does not exist\n' "$BRANCH" >&2
  exit 2
fi
git -C "$REPO_DIR" checkout -B "$BRANCH" "origin/$BRANCH"

commit="$(git -C "$REPO_DIR" rev-parse HEAD)"
printf '[2/4] Building commit %s\n' "$commit"

cd "$REPO_DIR"

printf '\n[3/4] Building and pushing %s...\n' "$SERVER_IMAGE"
docker buildx build --pull --progress=plain --platform linux/amd64 --push \
  -f server/Dockerfile \
  -t "$SERVER_IMAGE" \
  server

printf '\n[4/4] Building and pushing %s...\n' "$WEB_IMAGE"
docker buildx build --pull --progress=plain --platform linux/amd64 --push \
  -f web/Dockerfile \
  -t "$WEB_IMAGE" \
  web

printf '\nRemote build completed.\n'
printf '  Branch: %s\n' "$BRANCH"
printf '  Commit: %s\n' "$commit"
printf '  Server: %s\n' "$SERVER_IMAGE"
printf '  Web:    %s\n' "$WEB_IMAGE"
REMOTE_BUILD_SCRIPT

end_time="$(date +%s)"
elapsed=$((end_time - start_time))

printf '\nBuild and push completed in %dm%ds.\n' "$((elapsed / 60))" "$((elapsed % 60))"
printf '  Server: %s\n' "$SERVER_IMAGE"
printf '  Web:    %s\n' "$WEB_IMAGE"
