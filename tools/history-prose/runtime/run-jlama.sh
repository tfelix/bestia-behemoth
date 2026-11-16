#!/bin/sh
# Runs one job plan against the Jlama server. Usage: run-jlama.sh <plan.json> <sheets.jsonl> <suffix> <label> <model dir>
cd "$(dirname "$0")/.."
plan="$1"; sheets="$2"; suffix="$3"; label="$4"; model="$5"
jlama/build/install/jlama-server/bin/jlama-server "$model" 8081 > "results/$label-server.log" 2>&1 &
server=$!
for i in $(seq 1 600); do curl -sf 127.0.0.1:8081/health >/dev/null && break; sleep 1; done
pid=$(pgrep -f "JlamaServer $model" | head -1)
started=$(date +%s)
python3 prose.py --sheets "$sheets" --plan "$plan" --server http://127.0.0.1:8081 --label "$label" \
  --out "results/$label-$suffix.jsonl"
ended=$(date +%s)
peak=$(grep VmHWM /proc/$pid/status | awk '{print $2}')
echo "{\"label\": \"$label\", \"plan\": \"$plan\", \"wall_s\": $((ended - started)), \"peak_rss_kb\": $peak}" >> results/runs.jsonl
kill $server
