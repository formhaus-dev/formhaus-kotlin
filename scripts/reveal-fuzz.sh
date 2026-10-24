#!/bin/sh
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
core=${1:-$root/../formhaus/packages/core/dist/index.js}
count=${2:-50000}
output=$(mktemp)
trap 'rm -f "$output"' EXIT

node "$root/scripts/reveal-fuzz.mjs" "$core" "$output" "$count" "${REVEAL_SEED:-20261025}"
cd "$root"
FORMHAUS_REVEAL_FUZZ=$output ./gradlew --quiet :formhaus-core:test --tests '*RevealFuzzTest' --rerun
grep -ho 'reveal fuzz[^<]*' formhaus-core/build/test-results/test/TEST-dev.formhaus.core.corpus.RevealFuzzTest.xml
