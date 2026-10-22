#!/bin/sh
set -eu

jscpd_version=5.4.1

root=$(cd "$(dirname "$0")/.." && pwd)

run_ktlint() {
  "$root/gradlew" --no-daemon --quiet ktlintCheck
}

run_detekt() {
  "$root/gradlew" --no-daemon --quiet detekt
}

run_comments() {
  matches=$(grep -rnE '^\s*(//|/\*|\*)|[;{)]\s*//|\s//\s|/\*' --include='*.kt' --include='*.kts' \
    "$root/formhaus-core/src" "$root"/*.kts "$root/formhaus-core/build.gradle.kts" || true)
  if [ -n "$matches" ]; then
    echo "$matches" >&2
    echo "error: comments are not allowed" >&2
    exit 1
  fi
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
  all) run_ktlint && run_detekt && run_comments && run_duplicates ;;
  *)
    echo "usage: $0 [all|ktlint|detekt|comments|duplicates|versions]" >&2
    exit 1
    ;;
esac
