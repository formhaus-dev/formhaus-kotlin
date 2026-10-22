#!/bin/sh
set -eu

jscpd_version=5.4.1

root=$(cd "$(dirname "$0")/.." && pwd)

run_ktlint() {
  "$root/gradlew" --no-daemon --quiet ktlintCheck :build-logic:ktlintCheck
}

run_detekt() {
  "$root/gradlew" --no-daemon --quiet detekt :build-logic:detekt
}

run_comments() {
  matches=$(grep -rnE '^\s*(//|/\*|\*)|[;{)]\s*//|\s//\s|/\*' --include='*.kt' --include='*.kts' \
    "$root"/*/src "$root"/*.kts "$root"/*/*.kts || true)
  if [ -n "$matches" ]; then
    echo "$matches" >&2
    echo "error: comments are not allowed" >&2
    exit 1
  fi
}

run_tokens() {
  npm ci --silent --prefix "$root/tokens-build"
  node "$root/tokens-build/build.mjs" --check
}

run_duplicates() {
  npx --yes "jscpd@$jscpd_version" --config .jscpd.json
}

cd "$root"
case ${1:-all} in
  versions) echo "jscpd-$jscpd_version" ;;
  ktlint) run_ktlint ;;
  detekt) run_detekt ;;
  comments) run_comments ;;
  duplicates) run_duplicates ;;
  tokens) run_tokens ;;
  all) run_ktlint && run_detekt && run_comments && run_duplicates && run_tokens ;;
  *)
    echo "usage: $0 [all|ktlint|detekt|comments|duplicates|tokens|versions]" >&2
    exit 1
    ;;
esac
