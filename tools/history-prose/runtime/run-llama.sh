#!/bin/sh
# Runs one job plan against each model on llama.cpp, one model at a time, and records peak memory.
# Usage: run-llama.sh <plan.json> <sheets.jsonl> <suffix> <label=model.gguf>...
cd "$(dirname "$0")/.."
plan="$1"; sheets="$2"; suffix="$3"; shift 3
for pair in "$@"; do
  label="${pair%%=*}"; model="${pair#*=}"
  runtime/llama.sh "$model" 6 -tb 10 || exit 1
  pid=$(docker inspect -f '{{.State.Pid}}' bestia-llama)
  started=$(date +%s)
  python3 prose.py --sheets "$sheets" --plan "$plan" --server http://127.0.0.1:8080 \
    --label "$label" --out "results/$label-$suffix.jsonl"
  ended=$(date +%s)
  peak=$(grep VmHWM /proc/$pid/status | awk '{print $2}')
  echo "{\"label\": \"$label\", \"plan\": \"$plan\", \"wall_s\": $((ended - started)), \"peak_rss_kb\": $peak}" >> results/runs.jsonl
done
docker rm -f bestia-llama >/dev/null
