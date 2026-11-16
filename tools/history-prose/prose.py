"""Writes lore texts from fact sheets through an OpenAI-style chat endpoint, and validates each one.

Both runtimes of the spike serve this endpoint: llama.cpp's llama-server, and the small Jlama server in
jlama/. Each result line holds the text, every attempt, the validator result and the timings.

    python3 prose.py --sheets world.jsonl --plan plan.json --server http://127.0.0.1:8080 \
        --label qwen3-8b --out results/qwen3-8b.jsonl
"""
import argparse
import json
import time
import os
import urllib.request
import zlib

import validator

# Off by default, so the reading set and the world run stay reproducible. PROSE_VARY=1 turns it on.
VARY = os.environ.get("PROSE_VARY") == "1"

SYSTEM = """You write short texts that players find inside a fantasy game world: books, grave stones, legends, prayers.
Rules:
- Use only the facts in the FACT SHEET. Do not invent people, places, peoples, gods, artifacts or titles.
- Write every name exactly as the fact sheet writes it. Do not add titles like King or Lord.
- Write years as digits, and use only years and numbers from the fact sheet.
- You may invent feelings, images, weather and small details that carry no name and no number.
- Normal capital letters: every sentence starts with a capital, and names keep their capitals.
- Old, simple language. No modern words. No title line, no notes, no explanation. Output only the text."""

TASKS = {
    "epitaph": (
        "Write the epitaph carved on the grave of {name}. 2 to 5 short lines, 20 to 50 words in total. "
        "Name the dead, the years of birth and death, and one deed or tie from the fact sheet. Solemn tone."
    ),
    "legend": (
        "Write the legend of {name} as an old storyteller tells it at the fire. One paragraph, 80 to 130 words. "
        "Follow the events in order: who made it, of what, where, what became of it, and where it is now."
    ),
    "hymn": (
        "Write a hymn to {name}, the god of {domain}, as the {people} sing it. 6 to 10 short lines. "
        "Speak to the god by the name the {people} use. Draw on the god's domain and on anything else the fact "
        "sheet says about temples, cults or events."
    ),
    "book": (
        "Write a passage from a chronicle about the war between the {side} and the {other}. A court scribe of the "
        "{side} wrote it. "
        "One or two paragraphs, 100 to 180 words. The scribe is biased: praises the {side}, blames the {other}, "
        "and explains away every defeat of the {side}. But every event, winner, death count and year must stay "
        "as the fact sheet gives it. Do not give the scribe a name."
    ),
    "people": (
        "Write the opening passage of a chronicle of the {name}, as one of their own scholars wrote it. "
        "One or two paragraphs, 100 to 180 words. Proud of their past, honest about the years."
    ),
    "house": (
        "Write a passage from the family book of {name}. One paragraph, 80 to 150 words. "
        "Name the rulers in order with their years, and tell of the house's quarrels and ties."
    ),
    "memorial": (
        "Write the inscription on a memorial stone at {name}. 30 to 70 words. "
        "Name who fought here, who won, and how many fell."
    ),
    "tract": (
        "Write a passage from a tract of {name}, written by a believer. One paragraph, 80 to 130 words. "
        "Tell how the faith began, where it spread, and what its followers must do."
    ),
    "beast": (
        "Write the legend of {name} as a frightened villager tells it. One paragraph, 80 to 130 words. "
        "Follow the events in order and end with where it is now, as far as anyone knows."
    ),
}

# Several framings per text kind, picked by the entity's ref so a text keeps its framing on a rerun. One framing
# per kind made Gemma 4 E4B open 31 of 32 legends with the same line.
FRAMINGS = {
    "legend": [
        "an old storyteller tells it at the fire",
        "a temple keeper writes it in the temple's record of relics",
        "a traveller notes it down after hearing it in an inn",
        "a scholar writes it in a catalogue of famous things",
    ],
    "epitaph": [
        "carved on the grave",
        "cut into the stone above the barrow door",
        "written on a weathered marker by the grave",
    ],
}
# The phrase in TASKS that a framing replaces.
DEFAULT_FRAMING = {"legend": "as an old storyteller tells it at the fire", "epitaph": "carved on the grave"}
NO_FORMULA = " Do not open with 'Hark' or with a call to listen; start with the subject itself."

DROP_KEYS = {"ref", "names", "years", "id"}


def compact(value, indent=0):
    """The sheet as short indented lines. Fewer prompt tokens than JSON, and easier for a small model."""
    pad = "  " * indent
    lines = []
    if isinstance(value, dict):
        if set(value) >= {"year", "text"}:
            # Causes stay out of the prompt: they repeat earlier lines and cost a third of the tokens.
            return [f"{pad}- {value['year']}: {value['text']}"]
        for key, item in value.items():
            if key in DROP_KEYS or item in (None, [], {}):
                continue
            if isinstance(item, (dict, list)):
                lines.append(f"{pad}{key}:")
                lines.extend(compact(item, indent + 1))
            else:
                lines.append(f"{pad}{key}: {item}")
    elif isinstance(value, list):
        for item in value:
            if isinstance(item, dict) and not (set(item) >= {"year", "text"}):
                inner = compact(item, indent + 1)
                lines.append(f"{pad}-" + inner[0][len(pad) + 1:] if inner else f"{pad}-")
                lines.extend(inner[1:])
            elif isinstance(item, dict):
                lines.extend(compact(item, indent))
            else:
                lines.append(f"{pad}- {item}")
    return lines


def framing_for(job):
    options = FRAMINGS.get(job["type"])
    if not options or not VARY:
        return None
    return options[zlib.crc32(job["ref"].encode()) % len(options)]


def prompt_for(job, sheet):
    fields = {"name": sheet["name"], **job.get("args", {})}
    task = TASKS[job["type"]].format(**fields)
    framing = framing_for(job)
    if framing:
        prefix = "as " if job["type"] == "legend" else ""
        task = task.replace(DEFAULT_FRAMING[job["type"]], prefix + framing) + NO_FORMULA
    facts = "\n".join(compact(sheet))
    return f"FACT SHEET\n{facts}\n\nTASK\n{task}"


def chat(server, messages, max_tokens, temperature, timeout=1800):
    body = {
        "messages": messages,
        "max_tokens": max_tokens,
        "temperature": temperature,
        "top_p": 0.9,
        # Qwen3 thinks before it answers unless told not to. Thinking costs minutes on a CPU.
        "chat_template_kwargs": {"enable_thinking": False},
    }
    req = urllib.request.Request(
        f"{server}/v1/chat/completions", data=json.dumps(body).encode(), headers={"Content-Type": "application/json"}
    )
    started = time.time()
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        answer = json.loads(resp.read())
    wall = time.time() - started
    text = answer["choices"][0]["message"]["content"]
    text = text.split("</think>")[-1].strip()
    usage = answer.get("usage", {})
    timings = answer.get("timings", {})
    return text, {
        "wall_s": round(wall, 2),
        "prompt_tokens": timings.get("prompt_n", usage.get("prompt_tokens")),
        "gen_tokens": timings.get("predicted_n", usage.get("completion_tokens")),
        "prompt_tps": timings.get("prompt_per_second"),
        "gen_tps": timings.get("predicted_per_second"),
    }


def generate(job, sheet, server, attempts, temperature):
    prompt = prompt_for(job, sheet)
    history = []
    for attempt in range(1, attempts + 1):
        messages = [{"role": "system", "content": SYSTEM}, {"role": "user", "content": prompt}]
        text, timing = chat(server, messages, max_tokens=400, temperature=temperature)
        problems = validator.check_english(text, sheet)
        history.append({"attempt": attempt, "text": text, "problems": problems, "timing": timing})
        if not problems:
            break
        # A fresh prompt with the validator's findings added. Small models copy a rejected answer word for
        # word when it stays in the conversation, so the rejected text is not sent back.
        prompt = prompt_for(job, sheet) + "\n\nAVOID\n" + validator.feedback(problems)
    return history


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheets", required=True)
    ap.add_argument("--plan", required=True, help="JSON list of jobs: {ref, type, args}")
    ap.add_argument("--server", default="http://127.0.0.1:8080")
    ap.add_argument("--label", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--attempts", type=int, default=3)
    ap.add_argument("--temperature", type=float, default=0.7)
    ap.add_argument("--limit", type=int, default=0)
    args = ap.parse_args()

    sheets = {}
    for line in open(args.sheets, encoding="utf-8"):
        s = json.loads(line)
        sheets[s["ref"] or s.get("name")] = s
    jobs = json.load(open(args.plan, encoding="utf-8"))
    if args.limit:
        jobs = jobs[: args.limit]

    with open(args.out, "a", encoding="utf-8") as out:
        for i, job in enumerate(jobs):
            sheet = sheets[job["ref"]]
            started = time.time()
            history = generate(job, sheet, args.server, args.attempts, args.temperature)
            final = history[-1]
            record = {
                "label": args.label, "job": job, "name": sheet["name"], "text": final["text"],
                "passed": not final["problems"], "passed_first": not history[0]["problems"],
                "attempts": history, "wall_s": round(time.time() - started, 2),
            }
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
            out.flush()
            t = final["timing"]
            print(f"[{i + 1}/{len(jobs)}] {job['type']:8} {sheet['name'][:40]:40} "
                  f"tries={len(history)} ok={record['passed']} {record['wall_s']}s "
                  f"pp={t['prompt_tps']} tg={t['gen_tps']}", flush=True)


if __name__ == "__main__":
    main()
