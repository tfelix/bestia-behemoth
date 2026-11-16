#!/bin/sh
# The reading set with the v3 prompts (allowed names, cleaned facts, Battlefield names) on the two remaining models.
cd "$(dirname "$0")/.."
for pair in gemma4-e4b=gemma4-e4b/gemma-4-E4B-it-Q4_K_M.gguf qwen3-8b=qwen3-8b/Qwen3-8B-Q4_K_M.gguf; do
  label="${pair%%=*}"; model="${pair#*=}"
  runtime/llama.sh "$model" 6 -tb 10 --no-repack --cache-ram 0 || exit 1
  pid=$(docker inspect -f '{{.State.Pid}}' bestia-llama)
  started=$(date +%s)
  python3 prompt_v3.py --plan data/reading-plan.json --label "$label-v3" --out "results/$label-v3-reading.jsonl"
  echo "{\"label\": \"$label-v3\", \"plan\": \"data/reading-plan.json\", \"wall_s\": $(( $(date +%s) - started )), \"peak_rss_kb\": $(grep VmHWM /proc/$pid/status | awk '{print $2}')}" >> results/runs.jsonl
done
docker rm -f bestia-llama >/dev/null
echo CHAIN-DONE
