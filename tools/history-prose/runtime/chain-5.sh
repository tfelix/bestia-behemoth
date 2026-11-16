#!/bin/sh
# All legends of the world again, with rotating framings, to see whether Gemma 4 E4B's openings vary.
cd "$(dirname "$0")/.."
until grep -q '^CHAIN-DONE$' results/chain-4.log; do sleep 5; done
echo LEGENDS-VARIED
PROSE_VARY=1 runtime/run-llama.sh data/legend-plan.json data/genesis-11753242.jsonl legends-varied \
  gemma4-e4b=gemma4-e4b/gemma-4-E4B-it-Q4_K_M.gguf
echo CHAIN-DONE
