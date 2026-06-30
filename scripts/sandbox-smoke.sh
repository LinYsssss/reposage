#!/usr/bin/env bash
#
# sandbox-smoke.sh — proves the analyzed-container isolation guarantees enforced by ContainerPolicy.
#
# Requires a working Docker engine. Run from anywhere:
#   bash scripts/sandbox-smoke.sh
#
# It launches throwaway containers with the exact hardening flags the sandbox runner applies and
# asserts that network egress, cloud metadata, the Docker socket, and host writes are all blocked,
# while the dedicated tmpfs scratch area stays writable. Exit code 0 = all guarantees hold.
set -uo pipefail

IMAGE="${SANDBOX_IMAGE:-alpine:3.20}"
WORKSPACE="$(mktemp -d)"
echo "demo file" > "${WORKSPACE}/README"

HARDENING=(
  --rm
  --network none
  --read-only
  --user 65534:65534
  --cap-drop ALL
  --security-opt no-new-privileges
  --pids-limit 128
  --memory 256m
  --cpus 1
  --tmpfs /work/.tmp:rw,noexec,nosuid,size=64m
  -v "${WORKSPACE}:/work:ro"
  -w /work
)

fail=0

# Expect the command to FAIL (isolation holds when the operation is blocked).
expect_blocked() {
  local desc="$1"; shift
  if docker run "${HARDENING[@]}" "${IMAGE}" sh -c "$*" >/dev/null 2>&1; then
    echo "FAIL: ${desc} — operation unexpectedly succeeded"; fail=1
  else
    echo "OK:   ${desc} — blocked"
  fi
}

# Expect the command to SUCCEED (positive control).
expect_ok() {
  local desc="$1"; shift
  if docker run "${HARDENING[@]}" "${IMAGE}" sh -c "$*" >/dev/null 2>&1; then
    echo "OK:   ${desc}"
  else
    echo "FAIL: ${desc} — expected success"; fail=1
  fi
}

echo "== Sandbox isolation smoke test (image: ${IMAGE}) =="
expect_blocked "network egress"            'wget -T2 -q -O- http://example.com'
expect_blocked "cloud metadata endpoint"   'wget -T2 -q -O- http://169.254.169.254/latest/meta-data/'
expect_blocked "docker socket access"      'test -S /var/run/docker.sock'
expect_blocked "root filesystem write"     'touch /pwned'
expect_blocked "workspace write (read-only)" 'touch /work/pwned'
expect_ok      "workspace read"            'cat /work/README'
expect_ok      "scratch tmpfs write"       'touch /work/.tmp/scratch'

rm -rf "${WORKSPACE}"
if [ "${fail}" -eq 0 ]; then
  echo "== All sandbox isolation guarantees hold =="
else
  echo "== ISOLATION FAILURE — see FAIL lines above =="
fi
exit "${fail}"
