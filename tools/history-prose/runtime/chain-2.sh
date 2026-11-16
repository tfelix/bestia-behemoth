#!/bin/sh
cd "$(dirname "$0")/.."
S=data/genesis-11753242.jsonl
V="$HOME/llm-spike/venv/bin/python3"

echo JLAMA
rm -f results/jlama-mistral7b-reading.jsonl
runtime/run-jlama.sh data/reading-plan.json $S reading jlama-mistral7b "$HOME/llm-spike/models/jlama-mistral7b"

echo TRANSLATE-BOOKS
# Each model translates its own first book; Argos translates the same book.
for pair in qwen3-4b=qwen3-4b/Qwen3-4B-Q4_K_M.gguf qwen3-8b=qwen3-8b/Qwen3-8B-Q4_K_M.gguf ministral3-8b=ministral3-8b/Ministral-3-8B-Instruct-2512-Q4_K_M.gguf; do
  label="${pair%%=*}"; model="${pair#*=}"
  runtime/llama.sh "$model" 6 -tb 10 || exit 1
  python3 translate.py --in results/$label-reading.jsonl --out results/$label-de.jsonl --how llm --only book --limit 1
done
docker rm -f bestia-llama >/dev/null
jlama/build/install/jlama-server/bin/jlama-server "$HOME/llm-spike/models/jlama-mistral7b" 8081 > results/jlama-translate-server.log 2>&1 &
jl=$!
for i in $(seq 1 600); do curl -sf 127.0.0.1:8081/health >/dev/null && break; sleep 1; done
python3 translate.py --in results/jlama-mistral7b-reading.jsonl --out results/jlama-mistral7b-de.jsonl --how llm --only book --limit 1 --server http://127.0.0.1:8081
kill $jl
for label in qwen3-4b qwen3-8b ministral3-8b jlama-mistral7b; do
  $V translate.py --in results/$label-reading.jsonl --out results/$label-de.jsonl --how argos --only book --limit 1
done

echo WORLD
runtime/run-llama.sh data/world-plan.json $S world qwen3-8b=qwen3-8b/Qwen3-8B-Q4_K_M.gguf

echo TRANSLATE-SAMPLE
runtime/llama.sh qwen3-8b/Qwen3-8B-Q4_K_M.gguf 6 -tb 10 || exit 1
python3 translate.py --in results/qwen3-8b-world.jsonl --out results/qwen3-8b-world-de-llm.jsonl --how llm --limit 20
docker rm -f bestia-llama >/dev/null
$V translate.py --in results/qwen3-8b-world.jsonl --out results/qwen3-8b-world-de-argos.jsonl --how argos --limit 20
echo MEMORY
runtime/memory.sh qwen3-8b/Qwen3-8B-Q4_K_M.gguf $S data/reading-plan.json > results/memory.jsonl
echo CHAIN-DONE
