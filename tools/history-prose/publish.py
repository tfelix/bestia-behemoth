"""Builds the one-page reading set from bundle.json, the metrics and the scores.

    python3 render.py --sheets data/genesis-11753242.jsonl --out ../../docs/history-prose-samples
    python3 publish.py --bundle ../../docs/history-prose-samples/bundle.json --out ../../docs/history-prose-samples/index.html
"""
import argparse
import json
import subprocess

import prose
import translate
import validator

MODEL_NAMES = {
    "qwen3-8b": "Qwen3-8B · llama.cpp",
    "ministral3-8b": "Ministral-3-8B · llama.cpp",
    "qwen3-4b": "Qwen3-4B · llama.cpp",
    "ministral3-3b": "Ministral-3-3B · llama.cpp",
    "gemma4-e4b": "Gemma 4 E4B · llama.cpp",
    "jlama-mistral7b": "Mistral-7B v0.3 · Jlama",
}
ORDER = ["gemma4-e4b", "qwen3-8b", "ministral3-8b", "qwen3-4b", "ministral3-3b", "jlama-mistral7b"]
KIND_LABELS = {"book": "Chronicle book", "epitaph": "Epitaph", "legend": "Artifact legend", "hymn": "Hymn"}


def overall(s):
    return round((2 * s["facts"] + s["voice"] + s["language"] + s["form"]) / 5 * 2) / 2


def book_v2():
    """The war chronicles with the revised book and translation prompts, next to the original books."""
    import book_prompt_v2
    import os
    notes = json.load(open("data/book-v2-notes.json", encoding="utf-8"))
    rows = []
    for label in ("gemma4-e4b", "qwen3-8b"):
        path = f"results/{label}-book-v2.jsonl"
        if not os.path.exists(path):
            continue
        old = {r["job"]["args"]["side"]: r for r in map(json.loads, open(f"results/{label}-reading.jsonl"))
               if r["job"]["type"] == "book"}
        for r in map(json.loads, open(path, encoding="utf-8")):
            key = f"{label}/{r['side']}"
            rows.append({
                "model": MODEL_NAMES[label], "side": r["side"],
                "old": old[r["side"]]["text"], "new": r["text"], "attempts": len(r["attempts"]),
                "problems": [p for a in r["attempts"] for p in a["problems"]],
                "comment": notes["comments"].get(key),
                "de_v2": r["de_v2"], "de_v2_comment": notes["comments"].get(key + "/de_v2"),
                "de_v1": r["de_v1"], "de_v1_comment": notes["comments"].get(key + "/de_v1"),
            })
    return {
        "intro": notes["intro"], "summary": notes["summary"], "rows": rows,
        "prompts": [
            {"title": "Revised system prompt", "text": book_prompt_v2.SYSTEM_V2},
            {"title": "Revised task (user message: FACT SHEET, then this TASK)", "text": book_prompt_v2.TASK_V2},
            {"title": "Revised German localisation prompt (the whole user message, no system prompt)",
             "text": book_prompt_v2.TRANSLATE_V2},
        ],
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bundle", required=True)
    ap.add_argument("--scores", default="data/scores.json")
    ap.add_argument("--template", default="page.html")
    ap.add_argument("--texts", default="data/page-texts.json", help="lead, verdict and footer HTML")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    bundle = json.load(open(args.bundle, encoding="utf-8"))
    scores = json.load(open(args.scores, encoding="utf-8"))["scores"]
    texts = json.load(open(args.texts, encoding="utf-8"))
    metrics = json.loads(subprocess.check_output(["python3", "metrics.py"]))
    plan = json.load(open("data/reading-plan.json", encoding="utf-8"))

    models = [m for m in ORDER if m in bundle["models"]]
    summary = []
    for m in models:
        r = metrics.get(f"{m}-reading", {})
        s = scores.get(m, [])
        summary.append({
            "model": MODEL_NAMES[m],
            "valid": f"{r.get('passed')} of {r.get('texts')}",
            "first": f"{r.get('passed_first')} of {r.get('texts')}",
            "pp": r.get("prompt_tps"), "tg": r.get("gen_tps"),
            "per_text": r.get("seconds_per_text"),
            "rss": r.get("peak_rss_gb"),
            "score": f"{sum(overall(x) for x in s) / len(s):.1f}" if s else None,
        })

    page_texts = []
    for index, job in enumerate(plan):
        key = json.dumps(job, sort_keys=True)
        entry = bundle["texts"].get(key)
        if not entry:
            continue
        by = {}
        for m in models:
            r = entry["by"].get(m)
            if not r:
                continue
            s = scores.get(m, [None] * len(plan))[index] if index < len(scores.get(m, [])) else None
            by[m] = {
                **r,
                "score": overall(s) if s else None,
                "comment": s["comment"] if s else None,
                "de": [{"label": "LLM (same model)" if g["how"] == "llm" else "Argos (CTranslate2)",
                        "text": g["text"], "problems": g["problems"],
                        "comment": texts.get("de_comments", {}).get(f"{m}/{g['how']}")} for g in r["de"]],
            }
        heading = entry["title"].split(": ", 1)[1]
        task = "Task given to every model: " + prose.TASKS[job["type"]].format(
            **{"name": heading.split(" (told by")[0], **job.get("args", {})})
        page_texts.append({
            "title": entry["title"], "heading": heading, "kindLabel": KIND_LABELS.get(job["type"], job["type"]),
            "sheet": bundle["sheets"][job["ref"]], "task": task, "by": by,
        })

    data = {
        "experiment": book_v2(),
        "models": models, "modelNames": MODEL_NAMES,
        "lead": texts["lead"], "verdict": texts["verdict"], "footer": texts["footer"],
        "summaryColumns": [
            {"key": "model", "label": "Model · runtime"},
            {"key": "valid", "label": "Valid"},
            {"key": "first", "label": "Valid at once"},
            {"key": "pp", "label": "Prompt tok/s", "num": True},
            {"key": "tg", "label": "Gen tok/s", "num": True},
            {"key": "per_text", "label": "s per text", "num": True},
            {"key": "rss", "label": "Peak RSS GB", "num": True},
            {"key": "score", "label": "Score /5", "num": True},
        ],
        "summary": summary, "texts": page_texts,
        "prompts": [
            {"title": "System prompt (every text)", "text": prose.SYSTEM},
            *[{"title": f"Task: {KIND_LABELS.get(kind, kind)}", "text": template}
              for kind, template in prose.TASKS.items() if kind in KIND_LABELS],
            {"title": "User message layout",
             "text": "FACT SHEET\n<the fact sheet, as shown under each text>\n\nTASK\n<the task above, with names filled in>"},
            {"title": "Retry (added to the user message after a rejected try)",
             "text": "AVOID\n" + validator.feedback([
                 {"type": "name", "value": "Eldoria"}, {"type": "number", "value": "952"},
                 {"type": "case", "value": "keshband"}, {"type": "lowercase", "value": "the"}])
                     + "\n\n(The rejected text itself is not sent back: small models copied it word for word.)"},
            {"title": "Translation system prompt (LLM to German)", "text": translate.LLM_SYSTEM},
            {"title": "Sampling", "text": "temperature 0.7, top_p 0.9, max 400 new tokens; translation: temperature 0.2. "
                                          "Qwen3: thinking turned off (enable_thinking: false)."},
        ],
    }
    page = open(args.template, encoding="utf-8").read()
    payload = json.dumps(data, ensure_ascii=False).replace("</", "<\\/")
    open(args.out, "w", encoding="utf-8").write(page.replace("__DATA__", payload))
    print(f"wrote {args.out}")


if __name__ == "__main__":
    main()
