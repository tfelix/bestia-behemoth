#!/bin/sh
# The world run with the model the reading set favours, then the translation sample and the memory test.
cd "$(dirname "$0")/.."
S=data/genesis-11753242.jsonl
V="$HOME/llm-spike/venv/bin/python3"
M=gemma4-e4b/gemma-4-E4B-it-Q4_K_M.gguf

echo WORLD
runtime/run-llama.sh data/world-plan.json $S world gemma4-e4b=$M

echo TRANSLATE-SAMPLE
runtime/llama.sh $M 6 -tb 10 || exit 1
python3 translate.py --in results/gemma4-e4b-world.jsonl --out results/gemma4-e4b-world-de-llm.jsonl --how llm --limit 20
docker rm -f bestia-llama >/dev/null
$V translate.py --in results/gemma4-e4b-world.jsonl --out results/gemma4-e4b-world-de-argos.jsonl --how argos --limit 20

echo MEMORY
for model in $M qwen3-8b/Qwen3-8B-Q4_K_M.gguf; do
  runtime/memory.sh $model $S data/reading-plan.json >> results/memory.jsonl
done
echo CHAIN-DONE
