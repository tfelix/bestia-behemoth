# History prose spike

Prototype for phase 2 of the world history work: can a CPU-only server write lore texts from a world's
chronicle, and translate them? Spike code, not production code. The result is the bestia-docs page
`server/history-prose.md`; the texts are in `docs/history-prose-samples/`.

## Pieces

| File | What it does |
|---|---|
| `../../worldgen/.../viewer/FactSheets.kt` | `./gradlew :worldgen:chronicle -Pgenesis -Pfactsheet` writes one JSON fact sheet per entity |
| `plan.py` | Picks the texts to write: the reading set, or every text a world needs |
| `prose.py` | Writes each text through an OpenAI-style chat endpoint, validates it, retries |
| `validator.py` | Every name and number in a text must come from its fact sheet |
| `translate.py` | English to German: the same LLM, or Argos (CTranslate2) |
| `runtime/llama.sh` | Starts llama.cpp's server as a Docker sidecar |
| `runtime/run-llama.sh`, `runtime/run-jlama.sh` | Run a plan against one runtime and record peak memory |
| `runtime/bench.py`, `runtime/parallel.py` | Speed checks: thread count, parallel slots |
| `jlama/` | A small Jlama server with the same endpoint (its own Gradle build) |
| `metrics.py` | Pass rates, speed, memory, hours per world |
| `render.py`, `page.html`, `publish.py` | The reading set as Markdown and as one HTML page |

## Run it

Models, the llama.cpp image and the Argos package are not in the repo. The spike kept them in
`~/llm-spike/` (see the README there, and `~/llm-spike/cleanup.sh` to remove them).

```sh
./gradlew :worldgen:chronicle -Pgenesis -Pfactsheet
cd tools/history-prose
python3 plan.py data/genesis-11753242.jsonl > data/reading-plan.json
runtime/run-llama.sh data/reading-plan.json data/genesis-11753242.jsonl reading qwen3-8b=qwen3-8b/Qwen3-8B-Q4_K_M.gguf
(cd jlama && ../../../gradlew installDist)
runtime/run-jlama.sh data/reading-plan.json data/genesis-11753242.jsonl reading jlama-mistral7b ~/llm-spike/models/jlama-mistral7b
python3 metrics.py
python3 render.py --sheets data/genesis-11753242.jsonl --out ../../docs/history-prose-samples
```
