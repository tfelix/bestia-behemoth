#!/bin/sh
# Clean speed numbers per model with llama-bench (idle machine, no harness): prompt 512, generation 128,
# at an empty context and at 800 tokens of context, the size of a fact sheet.
for model in "$@"; do
  docker run --rm -v "$HOME/llm-spike/models:/models:ro" --entrypoint /app/llama ghcr.io/ggml-org/llama.cpp:server \
    bench -m "/models/$model" -p 512 -n 128 -t 6 -d 0,800 -r 2 -o jsonl 2>/dev/null
done
