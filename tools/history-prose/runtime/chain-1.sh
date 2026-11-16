#!/bin/sh
cd "$(dirname "$0")/.."
S=data/genesis-11753242.jsonl
runtime/run-jlama.sh data/reading-plan.json $S reading jlama-mistral7b "$HOME/llm-spike/models/jlama-mistral7b"
runtime/run-llama.sh data/reading-plan.json $S reading qwen3-4b=qwen3-4b/Qwen3-4B-Q4_K_M.gguf
echo BENCH
runtime/bench-all.sh qwen3-4b/Qwen3-4B-Q4_K_M.gguf qwen3-8b/Qwen3-8B-Q4_K_M.gguf ministral3-8b/Ministral-3-8B-Instruct-2512-Q4_K_M.gguf > results/bench.jsonl
echo MEMORY
runtime/memory.sh qwen3-8b/Qwen3-8B-Q4_K_M.gguf $S data/reading-plan.json > results/memory.jsonl
echo CHAIN-DONE
