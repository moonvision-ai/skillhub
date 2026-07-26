#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT="$ROOT_DIR/docker/docker_build.sh"
TMP_DIR="$(mktemp -d)"
FAKE_BIN="$TMP_DIR/bin"
CAPTURE_DIR="$TMP_DIR/capture"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p "$FAKE_BIN" "$CAPTURE_DIR"

cat > "$FAKE_BIN/git" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "branch" && "${2:-}" == "--show-current" ]]; then
  printf '%s\n' 'feature/configurable-auth-entry-policy'
  exit 0
fi

if [[ "${1:-}" == "check-ref-format" && "${2:-}" == "--branch" ]]; then
  [[ "${3:-}" != *' '* && "${3:-}" != *'..'* ]]
  exit
fi

exit 1
EOF

cat > "$FAKE_BIN/ssh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

: "${SSH_CAPTURE_DIR:?}"
printf '%s\n' "$*" > "$SSH_CAPTURE_DIR/args"
cat > "$SSH_CAPTURE_DIR/stdin"
EOF

chmod +x "$FAKE_BIN/git" "$FAKE_BIN/ssh"

failures=0

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  failures=$((failures + 1))
}

assert_contains() {
  local file="$1"
  local expected="$2"
  if ! grep -F -- "$expected" "$file" >/dev/null; then
    fail "$file does not contain: $expected"
  fi
}

run_script() {
  PATH="$FAKE_BIN:/usr/bin:/bin" SSH_CAPTURE_DIR="$CAPTURE_DIR" \
    YES=1 bash "$SCRIPT" "$@"
}

test_requires_tag() {
  local output="$TMP_DIR/requires-tag.out"
  if run_script >"$output" 2>&1; then
    fail 'missing TAG should fail'
  elif ! grep -F 'Usage:' "$output" >/dev/null; then
    fail 'missing TAG should print usage'
  fi
}

test_rejects_unsafe_inputs() {
  local output="$TMP_DIR/unsafe.out"

  if run_script 'bad tag' >"$output" 2>&1; then
    fail 'unsafe TAG should fail'
  elif ! grep -F 'Invalid TAG' "$output" >/dev/null; then
    fail 'unsafe TAG should explain the validation failure'
  fi

  if run_script valid 'bad branch' >"$output" 2>&1; then
    fail 'unsafe branch should fail'
  elif ! grep -F 'Invalid BRANCH' "$output" >/dev/null; then
    fail 'unsafe branch should explain the validation failure'
  fi

  if SSH_USER='bad;user' run_script valid main >"$output" 2>&1; then
    fail 'unsafe SSH_USER should fail'
  elif ! grep -F 'Invalid SSH_USER' "$output" >/dev/null; then
    fail 'unsafe SSH_USER should explain the validation failure'
  fi
}

test_builds_and_pushes_both_images() {
  local output="$TMP_DIR/success.out"
  : > "$CAPTURE_DIR/args"
  : > "$CAPTURE_DIR/stdin"

  SSH_USER=sam run_script v0.2.13-auth >"$output" 2>&1 || {
    fail 'valid invocation should succeed'
    return
  }

  assert_contains "$CAPTURE_DIR/args" '-J sam@59.110.17.213'
  assert_contains "$CAPTURE_DIR/args" 'sam@172.17.20.220'
  assert_contains "$CAPTURE_DIR/args" 'v0.2.13-auth'
  assert_contains "$CAPTURE_DIR/args" 'feature/configurable-auth-entry-policy'
  assert_contains "$CAPTURE_DIR/stdin" 'git@github.com:moonvision-ai/skillhub.git'
  assert_contains "$CAPTURE_DIR/stdin" 'registry.cn-beijing.aliyuncs.com/moonvision/skillhub-server'
  assert_contains "$CAPTURE_DIR/stdin" 'registry.cn-beijing.aliyuncs.com/moonvision/skillhub-web'
  assert_contains "$CAPTURE_DIR/stdin" 'docker buildx build --pull --progress=plain --platform linux/amd64 --push'
  assert_contains "$CAPTURE_DIR/stdin" '-f server/Dockerfile'
  assert_contains "$CAPTURE_DIR/stdin" '-f web/Dockerfile'
  assert_contains "$CAPTURE_DIR/stdin" 'status --porcelain'
}

test_requires_tag
test_rejects_unsafe_inputs
test_builds_and_pushes_both_images

if ((failures > 0)); then
  printf '%s test assertion(s) failed\n' "$failures" >&2
  exit 1
fi

printf 'docker build script tests passed\n'
