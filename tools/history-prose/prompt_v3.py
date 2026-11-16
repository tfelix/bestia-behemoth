"""Prompts in the restructured (v3) form for every text kind, built from the fact sheets.

Each prompt has hard rules in the system prompt, then the task, a list of the only allowed names with what each one
is, and a cleaned-up fact sheet. The allowed-names list is what a small model needs most: it tells it which Chuluun
it is writing about and what Bortedag is.

Names are resolved from the other fact sheets of the world, by name and by the years the name appears in. That is a
stopgap: the chronicle knows the actor of every event, and a real job should take the refs from there.

Battlefield sites are renamed from "X Field" to "X Battlefield" here, ahead of the same change in Names.kt.

    python3 prompt_v3.py --plan data/reading-plan.json --label gemma4-e4b-v3 --out results/gemma4-e4b-v3-reading.jsonl
"""
import argparse
import json
import re
import time
import urllib.request
import zlib

import validator

SYSTEM = """You are an in-world writer of short lore texts for a fantasy game: chronicles, grave stones, legends, hymns.
Your highest priority is strict factual accuracy based ONLY on the provided FACT SHEET.

STRICT VALIDATOR RULES:
1. ALLOWED PROPER NOUNS ONLY: You may ONLY use proper nouns listed in the ALLOWED NAMES list. Never invent names of people, places, gods, ranks, or titles (do NOT use words like King, Lord, General, or Emperor unless they appear in the ALLOWED NAMES list).
2. NUMBERS & YEARS: Write all years and numbers strictly as digits (e.g., 901, 406), never as words. Use ONLY numbers present in the FACT SHEET.
3. ALLOWED CREATIVITY: You may describe weather, emotions, landscape, and unnamed soldiers/crowds to create atmosphere.
4. STYLE: Write in simple, archaic prose. Avoid modern words.
5. OUTPUT FORMAT: Output ONLY the final text, in the length and shape the TASK asks. Do NOT include a title, intro, notes, or markdown formatting."""

USER = """TASK:
{task}

ALLOWED NAMES (Use exact spelling, use NO other proper nouns):
{allowed}

FACT SHEET:
{facts}

Write the text now:"""

TASKS = {
    "book": (
        "Write a biased chronicle passage about the war between the {side} and the {other}. 1 to 2 paragraphs, "
        "100 to 180 words.\n"
        "- Perspective: You are an unnamed scribe loyal to the {side}.\n"
        "- Bias: Praise the courage and righteousness of the {side}. Blame the {other} for the cruelty and the losses "
        "of the war, and portray their victories as dishonorable, costly, or temporary. Keep who declared the war as "
        "the FACT SHEET says.\n"
        "- Name every battle with its year, its battlefield and its death count, exactly as the FACT SHEET gives them."
    ),
    "epitaph": (
        "Write the epitaph {framing} of {name}. 2 to 5 short lines, 20 to 50 words in total. Name the dead, the years "
        "of birth and death, and one deed or tie from the FACT SHEET. Solemn tone."
    ),
    "legend": (
        "Write the legend of {name} as {framing}. One paragraph, 80 to 130 words. Follow the events in order: who made "
        "it, of what, where, what became of it, and where it is now. Start with the subject itself, not with a call to "
        "listen."
    ),
    "hymn": (
        "Write a hymn to {name}, the god of {domain}, as the {people} sing it. 6 to 10 short lines. Speak to the god by "
        "the name the {people} use. Draw on the god's domain and on what the FACT SHEET says about temples, cults or "
        "events."
    ),
}

FRAMINGS = {
    "legend": ["an old storyteller tells it at the fire", "a temple keeper writes it in the record of relics",
               "a traveller notes it down after hearing it in an inn", "a scholar writes it in a catalogue of famous things"],
    "epitaph": ["carved on the grave", "cut into the stone above the barrow door", "written on a weathered marker by the grave"],
}

SAMPLING = {"temperature": 0.6, "top_p": 0.85, "min_p": 0.05, "repeat_penalty": 1.08}


# --- Battlefield naming ---------------------------------------------------------------------------------------------

def battlefield_names(sheets):
    return {s["name"] for s in sheets if s["kind"] == "battle"}


def rename_battlefields(value, fields):
    """ "Bortedag Field" -> "Bortedag Battlefield", everywhere in a sheet."""
    if isinstance(value, dict):
        return {k: rename_battlefields(v, fields) for k, v in value.items()}
    if isinstance(value, list):
        return [rename_battlefields(v, fields) for v in value]
    if isinstance(value, str):
        for field in fields:
            value = value.replace(field, field[: -len("Field")] + "Battlefield")
        return value
    return value


# --- The world's names, and what each one is ------------------------------------------------------------------------

def name_index(sheets):
    """Every rendered name in the world, with a short description and the years it was around."""
    index = {}

    def add(name, what, years=None):
        if not name:
            return
        index.setdefault(name, [])
        if not any(e["what"] == what for e in index[name]):
            index[name].append({"what": what, "years": years})

    for s in sheets:
        kind = s["kind"]
        if kind == "civ":
            add(s["name"], f"a people, {s['culture']}, since {s['founded']}" + (f", gone since {s['ended']}" if s.get("ended") else ""))
            add(s.get("capital"), f"capital of the {s['name']}")
        elif kind == "figure":
            end = s.get("died") or 9999
            life = f"{s['born']}-{s['died']}" if s.get("died") else f"born {s['born']}, alive"
            add(s["name"], f"{s['role']} of the {s['people']}, {life}", (s["born"], end))
            add(s.get("home"), "a town")
            if s.get("grave"):
                add(s["grave"]["name"], f"grave of {s['name']}")
        elif kind == "dynasty":
            add(s["name"], f"a ruling house of the {s['people']}")
            add(s.get("seat"), "a town")
        elif kind == "artifact":
            add(s["name"], f"a {s['form']} of {s['material']}, made {s['forged']}")
            add(s.get("forgedAt"), "a town")
        elif kind == "battle":
            add(s["name"], f"a battlefield near {s.get('near')}")
            add(s.get("near"), "a town")
        elif kind == "deity":
            for n in s.get("namesByPeople", []):
                add(n["name"], f"god of {s['domain']}, as the {n['people']} call it")
            for t in s.get("templesIn", []):
                add(t, "a town")
        elif kind == "cult":
            add(s["name"], "a cult")
            add(s.get("foundedAt"), "a town")
            add(s.get("sanctuary"), "a sanctuary")
        elif kind == "monster":
            add(s["name"], f"{s['species']}, a great beast")
    return index


def sheet_years(sheet, name):
    """The years of the event lines that mention a name."""
    return [e["year"] for e in sheet.get("events", []) if re.search(rf"\b{re.escape(name)}\b", e["text"])]


def describe(entries, sheet, name):
    """One description per name: the entries that fit this sheet, merged."""
    if len(entries) > 1 and any(e["years"] for e in entries):
        years = sheet_years(sheet, name) or list(sheet.get("years", []))
        fitting = [e for e in entries if e["years"] and any(e["years"][0] <= y <= e["years"][1] for y in years)]
        entries = fitting or entries
    # A barrow name is shared by every grave in it; the one that matters is the subject's.
    about_subject = [e for e in entries if sheet["name"] in e["what"]]
    entries = about_subject or entries
    whats = [e["what"] for e in entries]
    if len(whats) > 1 and "a town" in whats:
        whats.remove("a town")
    gods = [w for w in whats if w.startswith("god of") and ", as the " in w]
    if len(gods) > 1:
        peoples = [w.split(", as the ")[1].removesuffix(" call it") for w in gods]
        whats = [w for w in whats if w not in gods] + [
            f"{gods[0].split(', as the ')[0]}, as the {', the '.join(peoples[:-1])} and the {peoples[-1]} call it"]
    return "; or ".join(whats)


def allowed_names(sheet, index):
    text = json.dumps(sheet, ensure_ascii=False)
    found = [n for n in index if re.search(rf"(?<![\w']){re.escape(n)}(?![\w'])", text)]
    # Names that only an event line knows, like a town under the name it had before a conquest.
    covered = " ".join(found)
    for e in sheet.get("events", []):
        for word in re.findall(r"\b[A-Z][a-z]+\b", e["text"]):
            if word not in covered and word.lower() not in validator.ENGLISH_WORDS and word not in index:
                index[word] = [{"what": "a place or person named in the chronicle", "years": None}]
                found.append(word)
                covered += " " + word
    # A short name inside a longer one ("Gerel" in "Gerel the Burned") is only allowed when it also stands alone.
    standalone = []
    for n in found:
        stripped = text
        for longer in (m for m in found if m != n and n in m):
            stripped = stripped.replace(longer, "")
        if re.search(rf"(?<![\w']){re.escape(n)}(?![\w'])", stripped):
            standalone.append(n)
    lines = [f"- {n} ({describe(index[n], sheet, n)})" for n in sorted(standalone)]
    return "\n".join(lines), standalone


# --- The cleaned-up fact sheet --------------------------------------------------------------------------------------

def facts(sheet):
    lines = []
    skip = {"ref", "kind", "names", "years", "events", "name"}
    if sheet["kind"] == "war":
        a, b = (side["people"] for side in sheet["sides"])
        end = f"-{sheet['peace']}" if sheet.get("peace") else ""
        lines.append(f"- Subject: the war between the {a} and the {b}, {sheet['declared']}{end}")
    else:
        lines.append(f"- Subject: {sheet['name']}")
    for key, value in sheet.items():
        if key in skip or value in (None, [], {}):
            continue
        if isinstance(value, list) and value and isinstance(value[0], dict):
            for item in value:
                inner = ", ".join(f"{k}: {v}" for k, v in item.items() if v not in (None, [], {}) and k != "event")
                if isinstance(item.get("event"), dict):
                    inner += f" ({item['event']['year']}: {item['event']['text']})"
                lines.append(f"- {key}: {inner}")
        elif isinstance(value, list):
            lines.append(f"- {key}: {', '.join(map(str, dict.fromkeys(value)))}")
        elif isinstance(value, dict):
            lines.append(f"- {key}: " + ", ".join(f"{k}: {v}" for k, v in value.items()))
        else:
            lines.append(f"- {key}: {value}")
    events = sheet.get("events", [])
    if sheet["kind"] == "war":
        # The war sheet lists each battle's field in battle order; put the field into its battle line.
        fields = iter(sheet.get("fields", []))
        events = [dict(e, text=re.sub(r"(near \w+)", rf"\1, on {next(fields, '')}", e["text"], count=1)
                       .replace(", on ,", ",")) if e["kind"] == "battle" else e for e in events]
    if events:
        lines.append("- Chronology of events:")
        lines += [f"  * {e['year']}: {e['text'][0].upper()}{e['text'][1:]}." for e in events]
    return "\n".join(l for l in lines if not l.startswith("- fields:"))


# --- Running it -----------------------------------------------------------------------------------------------------

def task_for(job, sheet):
    args = {"name": sheet["name"], **job.get("args", {})}
    options = FRAMINGS.get(job["type"])
    if options:
        args["framing"] = options[zlib.crc32(job["ref"].encode()) % len(options)]
    return TASKS[job["type"]].format(**args)


def prompt_for(job, sheet, index):
    allowed, names = allowed_names(sheet, index)
    return USER.format(task=task_for(job, sheet), allowed=allowed, facts=facts(sheet)), names


def chat(server, messages, max_tokens=400):
    body = {"messages": messages, "max_tokens": max_tokens, **SAMPLING, "chat_template_kwargs": {"enable_thinking": False}}
    req = urllib.request.Request(f"{server}/v1/chat/completions", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    started = time.time()
    with urllib.request.urlopen(req, timeout=1800) as resp:
        answer = json.loads(resp.read())
    t = answer.get("timings", {})
    return answer["choices"][0]["message"]["content"].split("</think>")[-1].strip(), {
        "wall_s": round(time.time() - started, 2), "prompt_tokens": t.get("prompt_n"), "gen_tokens": t.get("predicted_n"),
        "prompt_tps": t.get("prompt_per_second"), "gen_tps": t.get("predicted_per_second")}


def load_world(path):
    sheets = [json.loads(l) for l in open(path, encoding="utf-8")]
    fields = battlefield_names(sheets)
    sheets = [rename_battlefields(s, fields) for s in sheets]
    return {s["ref"] or s["name"]: s for s in sheets}, name_index(sheets)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--plan", required=True)
    ap.add_argument("--sheets", default="data/genesis-11753242.jsonl")
    ap.add_argument("--server", default="http://127.0.0.1:8080")
    ap.add_argument("--label", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--attempts", type=int, default=3)
    ap.add_argument("--show", action="store_true", help="print the prompts and stop")
    args = ap.parse_args()

    sheets, index = load_world(args.sheets)
    jobs = json.load(open(args.plan, encoding="utf-8"))
    if args.show:
        for job in jobs:
            print(SYSTEM if job is jobs[0] else "", prompt_for(job, sheets[job["ref"]], index)[0], sep="\n\n", end="\n\n=====\n")
        return

    with open(args.out, "a", encoding="utf-8") as out:
        for i, job in enumerate(jobs):
            sheet = sheets[job["ref"]]
            user, names = prompt_for(job, sheet, index)
            # The validator accepts what the prompt allowed: the sheet plus the allowed names.
            checked = dict(sheet, allowed=names)
            history, started = [], time.time()
            for attempt in range(1, args.attempts + 1):
                content = user if not history else user + "\n\nAVOID\n" + validator.feedback(history[-1]["problems"])
                text, timing = chat(args.server, [{"role": "system", "content": SYSTEM}, {"role": "user", "content": content}])
                problems = validator.check_english(text, checked)
                history.append({"attempt": attempt, "text": text, "problems": problems, "timing": timing})
                if not problems:
                    break
            record = {"label": args.label, "job": job, "name": sheet["name"], "text": history[-1]["text"],
                      "passed": not history[-1]["problems"], "passed_first": not history[0]["problems"],
                      "attempts": history, "wall_s": round(time.time() - started, 2), "prompt": user}
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
            out.flush()
            print(f"[{i + 1}/{len(jobs)}] {job['type']:8} {sheet['name'][:40]:40} tries={len(history)} "
                  f"ok={record['passed']} {record['wall_s']}s", flush=True)


if __name__ == "__main__":
    main()
