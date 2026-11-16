#!/bin/sh
# Stops chain 3 at its world run and starts chain 4 instead.
cd "$(dirname "$0")/.."
until grep -q '^WORLD$' results/chain-3.log; do sleep 3; done
for pid in $(pgrep -f 'runtime/chain-3.sh') $(pgrep -f 'run-llama.sh data/world-plan') $(pgrep -f 'plan data/world-plan'); do kill $pid; done
sleep 2
docker rm -f bestia-llama >/dev/null 2>&1
rm -f results/qwen3-8b-world.jsonl
echo HANDOVER
exec runtime/chain-4.sh
