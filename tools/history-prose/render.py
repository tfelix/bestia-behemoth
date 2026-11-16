"""Writes the reading set: one Markdown file per model and runtime, each text next to its fact sheet and its
validator result, plus a JSON bundle for the HTML page.

    python3 render.py --sheets data/genesis-11753242.jsonl --out ../../docs/history-prose-samples
"""
import argparse
import glob
import json
import os

import prose

TYPE_TITLES = {
    "book": "Chronicle book", "epitaph": "Epitaph", "legend": "Artifact legend", "hymn": "Hymn",
}


def load_jsonl(path):
    return [json.loads(l) for l in open(path, encoding="utf-8")] if os.path.exists(path) else []


def title_of(record):
    job = record["job"]
    title = f'{TYPE_TITLES.get(job["type"], job["type"])}: {record["name"]}'
    if job["type"] == "book":
        title += f' (told by the {job["args"]["side"]})'
    return title


def validator_line(record):
    tries = record["attempts"]
    verdict = "passed" if record["passed"] else "FAILED"
    out = [f"**Validator:** {verdict} after {len(tries)} attempt(s)."]
    for t in tries:
        if t["problems"]:
            found = ", ".join(f'{p["type"]} "{p["value"]}"' for p in t["problems"])
            out.append(f"- attempt {t['attempt']}: {found}")
    timing = tries[-1]["timing"]
    out.append(
        f"- last attempt: {timing['prompt_tokens']} prompt tokens at {timing['prompt_tps']:.1f}/s, "
        f"{timing['gen_tokens']} generated at {timing['gen_tps']:.1f}/s; whole text {record['wall_s']:.0f} s"
    )
    return "\n".join(out)


def markdown_for(label, records, translations, sheets):
    lines = [f"# Reading set: {label}", ""]
    passed = sum(r["passed"] for r in records)
    first = sum(r["passed_first"] for r in records)
    lines += [f"{len(records)} texts. Validator: {first} passed at once, {passed} after retries.", ""]
    for r in records:
        lines += [f"## {title_of(r)}", "", "\n".join(f"> {l}" if l else ">" for l in r["text"].split("\n")), ""]
        lines += [validator_line(r), ""]
        for t in translations:
            if t["job"] == r["job"]:
                lines += [f"**German ({t['how']}):**", ""]
                lines += ["\n".join(f"> {l}" if l else ">" for l in t["de"].split("\n")), ""]
                if t["problems"]:
                    lines += ["Names or numbers lost: " + ", ".join(p["value"] for p in t["problems"]), ""]
        sheet = sheets[r["job"]["ref"]]
        lines += ["<details><summary>Fact sheet, as the model saw it</summary>", "", "```text",
                  prose.prompt_for(r["job"], sheet), "```", "", "</details>", ""]
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheets", required=True)
    ap.add_argument("--results", default="results")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    sheets = {s["ref"]: s for s in load_jsonl(args.sheets)}
    os.makedirs(args.out, exist_ok=True)
    bundle = {"models": [], "texts": {}, "sheets": {}}
    for path in sorted(glob.glob(os.path.join(args.results, "*-reading.jsonl"))):
        records = load_jsonl(path)
        if not records:
            continue
        label = records[0]["label"]
        translations = load_jsonl(os.path.join(args.results, f"{label}-de.jsonl"))
        with open(os.path.join(args.out, f"{label}.md"), "w", encoding="utf-8") as f:
            f.write(markdown_for(label, records, translations, sheets))
        bundle["models"].append(label)
        for r in records:
            key = json.dumps(r["job"], sort_keys=True)
            entry = bundle["texts"].setdefault(key, {"title": title_of(r), "job": r["job"], "by": {}})
            entry["by"][label] = {
                "text": r["text"], "passed": r["passed"], "attempts": len(r["attempts"]),
                "problems": [p for t in r["attempts"] for p in t["problems"]],
                "timing": r["attempts"][-1]["timing"], "wall_s": r["wall_s"], "prompt": r.get("prompt"),
                "de": [{"how": t["how"], "text": t["de"], "problems": t["problems"]}
                       for t in translations if t["job"] == r["job"]],
            }
            bundle["sheets"][r["job"]["ref"]] = prose.prompt_for(r["job"], sheets[r["job"]["ref"]]).split("\n\nTASK")[0]
    with open(os.path.join(args.out, "bundle.json"), "w", encoding="utf-8") as f:
        json.dump(bundle, f, ensure_ascii=False, indent=1)
    print(f"{len(bundle['models'])} models, {len(bundle['texts'])} texts -> {args.out}")


if __name__ == "__main__":
    main()
