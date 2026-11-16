#!/bin/sh
# German for two texts of every kind from the world run: Gemma 4 E4B itself, then Argos.
cd "$(dirname "$0")/.."
until grep -q '^CHAIN-DONE$' results/chain-5.log; do sleep 5; done
echo TRANSLATE-MIXED
runtime/llama.sh gemma4-e4b/gemma-4-E4B-it-Q4_K_M.gguf 6 -tb 10 || exit 1
python3 translate.py --in results/gemma4-e4b-world.jsonl --out results/gemma4-e4b-mixed-de-llm.jsonl --how llm --per-kind 2
docker rm -f bestia-llama >/dev/null
"$HOME/llm-spike/venv/bin/python3" translate.py --in results/gemma4-e4b-world.jsonl --out results/gemma4-e4b-mixed-de-argos.jsonl --how argos --per-kind 2
echo CHAIN-DONE
