"""Sums up every result file: pass rates, speed, memory, and the time one world would take.

    python3 metrics.py [results dir]
"""
import glob
import json
import os
import statistics
import sys

# Texts one world needs, from `chronicle -Pfactsheet` on the genesis world and on a 256-cell world.
WORLD_TEXTS = {"genesis": 109, "large": 250}


def load(path):
    return [json.loads(l) for l in open(path, encoding="utf-8")]


def summarise(records, runs):
    first = [r["attempts"][0]["timing"] for r in records]
    walls = [r["wall_s"] for r in records]
    words = [len(r["text"].split()) for r in records if r["passed"]]
    peak = max((run["peak_rss_kb"] for run in runs), default=None)
    per_text = statistics.mean(walls)
    return {
        "texts": len(records),
        "passed_first": sum(r["passed_first"] for r in records),
        "passed": sum(r["passed"] for r in records),
        "attempts_mean": round(statistics.mean(len(r["attempts"]) for r in records), 2),
        # First attempts only: a retry hits the server's prompt cache and reads only a few new tokens.
        "prompt_tokens_mean": round(statistics.mean(t["prompt_tokens"] for t in first)),
        "prompt_tps": round(statistics.mean(t["prompt_tps"] for t in first), 1),
        "gen_tps": round(statistics.mean(t["gen_tps"] for t in first), 2),
        "seconds_per_text": round(per_text, 1),
        "words_mean": round(statistics.mean(words)) if words else 0,
        "peak_rss_gb": round(peak / 1024 / 1024, 2) if peak else None,
        "genesis_hours": round(per_text * WORLD_TEXTS["genesis"] / 3600, 1),
        "large_hours": round(per_text * WORLD_TEXTS["large"] / 3600, 1),
    }


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "results"
    runs = load(os.path.join(root, "runs.jsonl")) if os.path.exists(os.path.join(root, "runs.jsonl")) else []
    out = {}
    for path in sorted(glob.glob(os.path.join(root, "*-reading.jsonl")) + glob.glob(os.path.join(root, "*-world.jsonl"))):
        records = load(path)
        if not records:
            continue
        name = os.path.basename(path)[: -len(".jsonl")]
        label = records[0]["label"]
        plan = "world" if name.endswith("-world") else "reading"
        out[name] = summarise(records, [r for r in runs if r["label"] == label and plan in r["plan"]])
    print(json.dumps(out, indent=1))


if __name__ == "__main__":
    main()
