#!/bin/sh
# Stops chain 2 when it reaches the world run, waits for the small models, then starts chain 3.
cd "$(dirname "$0")/.."
until grep -q '^WORLD$' results/chain-2.log; do sleep 5; done
pkill -f 'runtime/chain-2.sh'; pkill -f 'runtime/run-llama.sh data/world-plan'; pkill -f 'prose.py --sheets data/genesis-11753242.jsonl --plan data/world-plan'
docker rm -f bestia-llama >/dev/null 2>&1
rm -f results/qwen3-8b-world.jsonl
until [ "$(stat -c %s "$HOME/llm-spike/models/gemma4-e4b/gemma-4-E4B-it-Q4_K_M.gguf" 2>/dev/null)" = "4977169088" ] || [ -f "$HOME/llm-spike/dl-done" ]; do sleep 10; done
echo HANDOVER
exec runtime/chain-3.sh
