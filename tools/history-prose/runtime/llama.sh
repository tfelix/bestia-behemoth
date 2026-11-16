#!/bin/sh
# Starts llama.cpp's server as a sidecar container. Usage: llama.sh <model path under ~/llm-spike/models> [threads] [extra args]
model="$1"; threads="${2:-6}"; shift 2 2>/dev/null; extra="$*"
docker rm -f bestia-llama >/dev/null 2>&1
docker run -d --name bestia-llama -p 127.0.0.1:8080:8080 -v "$HOME/llm-spike/models:/models:ro" \
  ghcr.io/ggml-org/llama.cpp:server -m "/models/$model" -c 4096 -t "$threads" -np 1 --host 0.0.0.0 --port 8080 $extra >/dev/null
for i in $(seq 1 120); do curl -sf 127.0.0.1:8080/health >/dev/null && exit 0; sleep 1; done
echo "server did not come up" >&2; docker logs bestia-llama 2>&1 | tail -20 >&2; exit 1
