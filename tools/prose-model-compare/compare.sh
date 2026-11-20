#!/bin/bash
# Runs the news texts once per model, one after the other. Args: output dir, then the runs to do (default all).
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
run() { # name model penalty memoryGb [file]
  echo "=== $1 start $(date +%T)"
  PROSE_REAL=true PROSE_MODEL="$2" PROSE_REPEAT_PENALTY="$3" PROSE_MEMORY_GB="$4" PROSE_MODEL_FILE="$5" \
    ./gradlew :zone-server:cleanTest :zone-server:test --tests 'net.bestia.zone.questgen.NewsTextsManualTest' -i > "$S/cmp-$1.log" 2>&1
  echo "=== $1 exit $? end $(date +%T)"
  grep 'NEWS TOTAL' "$S/cmp-$1.log"; echo "rejected: $(grep -c REJECTED "$S/cmp-$1.log")"
}
S="$1"; shift
RUNS="${*:-e4b-q4km e4b-qat 12b-qat}"
for name in $RUNS; do
  case $name in
    e4b-q4km) run e4b-q4km "unsloth/gemma-4-E4B-it-GGUF:Q4_K_M" 1.08 8 ;;
    e4b-qat)  run e4b-qat  "google/gemma-4-E4B-it-qat-q4_0-gguf" 1.0 8 "gemma-4-E4B_q4_0-it.gguf" ;;
    e4b-qat-rp105) run e4b-qat-rp105 "google/gemma-4-E4B-it-qat-q4_0-gguf" 1.05 8 "gemma-4-E4B_q4_0-it.gguf" ;;
    12b-qat)  run 12b-qat  "google/gemma-4-12B-it-qat-q4_0-gguf:Q4_0" 1.0 14 ;;
  esac
done
