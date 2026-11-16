#!/bin/sh
# The colleague-structured war-chronicle prompt (v3), on the two remaining models.
cd "$(dirname "$0")/.."
for pair in gemma4-e4b=gemma4-e4b/gemma-4-E4B-it-Q4_K_M.gguf qwen3-8b=qwen3-8b/Qwen3-8B-Q4_K_M.gguf; do
  label="${pair%%=*}"; model="${pair#*=}"
  runtime/llama.sh "$model" 6 -tb 10 --no-repack --cache-ram 0 || exit 1
  python3 book_prompt_v3.py --label "$label"
done
docker rm -f bestia-llama >/dev/null
echo CHAIN-DONE
