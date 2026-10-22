#!/bin/sh
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
out="$root/benchmarks/build/results"
gradle() {
  nice -n 15 "$root/gradlew" --no-daemon --quiet "$@"
}

smoke=0
part=all
for arg in "$@"; do
  case $arg in
    --smoke) smoke=1 ;;
    all | jvm | v8 | render) part=$arg ;;
    *)
      echo "usage: $0 [all|jvm|v8|render] [--smoke]" >&2
      exit 1
      ;;
  esac
done

if [ "$smoke" = 1 ]; then
  config=smoke
  java_args="--warmups 0 --runs 1 --cap 1"
  export FORMHAUS_RENDER_WARMUPS=1 FORMHAUS_RENDER_RUNS=2
else
  config=main
  java_args=""
fi

mkdir -p "$out"
cd "$root"

if [ "$part" = all ] || [ "$part" = v8 ]; then
  if [ "$smoke" = 1 ]; then
    nice -n 15 node benchmarks/v8/regex.mjs --warmups 1 --runs 2 --batch-ms 5 --cap 1 --json "$out/v8-regex.json"
  else
    nice -n 15 node benchmarks/v8/regex.mjs --json "$out/v8-regex.json"
  fi
  echo
fi

if [ "$part" = all ] || [ "$part" = jvm ]; then
  if [ "$config" = main ]; then gradle :benchmarks:benchmark; else gradle :benchmarks:smokeBenchmark; fi
  report=$(find "$root/benchmarks/build/reports/benchmarks/$config" -name main.json | sort | tail -n 1)
  gradle :benchmarks:benchmarkReport --args="--jmh $report --v8 $out/v8-regex.json $java_args"
  echo
fi

if [ "$part" = all ] || [ "$part" = render ]; then
  FORMHAUS_RENDER_BENCHMARKS=1 gradle :benchmarks-compose:testDebugUnitTest --tests '*RenderBenchmark' --rerun
  cat "$root/benchmarks-compose/build/reports/render-benchmarks.md"
fi
