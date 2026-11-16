"""The war chronicles a third time, with a colleague's prompt structure: hard rules in the system prompt, an
explicit list of allowed names with what each one is, a cleaned-up fact sheet, a bias anchor, and the task last.
Sampling as the colleague advised. German with a localisation prompt written for the model.

The cleaned-up sheet is written for this one war, from what the chronicle says (see WAR_FACTS). A real job would
build it from the chronicle's actor refs; that needs the events to carry refs, which the fact sheets do not have yet.

    python3 book_prompt_v3.py --label gemma4-e4b --server http://127.0.0.1:8080
"""
import argparse
import json
import time

import validator

WAR_REF = "war#409"

TRANSLATE = """Du bist ein professioneller deutscher Videospiel-Lokalisierer für ein Fantasy-MMORPG. 
Übertrage den folgenden englischen Chronik-Text in natürliches, atmosphärisches und idiomatisches Deutsch.

Regeln:
1. Übersetze NICHT Wort für Wort, sondern formuliere so, wie es in einem deutschen Fantasy-Roman stehen würde.
2. Verwende bei Fraktionen und Gruppen immer passende deutsche Artikel (z. B. "die Saryhorde" oder "die Keshband").
3. Verändere KEINE Eigennamen, Ortsnamen oder Zahlen (z.B. 901, Bortedag).
4. Gib ausschließlich den fertigen deutschen Text ohne Kommentare aus.

Englischer Text:
"{text}"
"""

SYSTEM_V3 = """You are an in-world chronicler writing short lore texts for a fantasy game.
Your highest priority is strict factual accuracy based ONLY on the provided FACT SHEET.

STRICT VALIDATOR RULES:
1. ALLOWED PROPER NOUNS ONLY: You may ONLY use proper nouns listed in the ALLOWED NAMES list. Never invent names of people, places, gods, ranks, or titles (do NOT use words like King, Lord, General, or Emperor unless they appear in the ALLOWED NAMES list).
2. NUMBERS & YEARS: Write all years and numbers strictly as digits (e.g., 901, 406), never as words. Use ONLY numbers present in the FACT SHEET.
3. ALLOWED CREATIVITY: You may describe weather, emotions, landscape, and unnamed soldiers/crowds to create atmosphere.
4. STYLE: Write in simple, archaic chronicle prose. Avoid modern words.
5. OUTPUT FORMAT: Output ONLY the final chronicle text (1 to 2 paragraphs, 100 to 180 words). Do NOT include a title, intro, notes, or markdown formatting."""

# Checked against the chronicle: four figures share the name Chuluun; the one who fell in 901 is the Saryhorde
# ruler (841-901). The 906 battle was fought on Steingrube Field, an older field; the 901 and 911 battles on two
# different sites that are both called Bortedag Field. Bayan is the patron of all four peoples of this world.
WAR_FACTS = """- Timeline: War declared in 901; peace terms agreed in 911.
- Saryhorde: Capital is Bortebulak; led by Batu at the start; won 0 battles; patron god is Bayan the Unseen Weaver.
- Keshband: Capital is Penpike; led by Tsetseg the Second at the start; won 3 battles; patron god is Bayan the Unseen Weaver. Both peoples worship the same god.
- Chronology of Events:
  * 901: The Saryhorde go to war against the Keshband.
  * 901: Near Bortedag, on Bortedag Field, the Keshband defeat the Saryhorde (406 dead). Chuluun, ruler of the Saryhorde, falls in battle. The Keshband sack Bortedag.
  * 906: Near Bortedag, on Steingrube Field, the Keshband defeat the Saryhorde (408 dead) and sack Bortedag again.
  * 911: Near Bortedag, on Bortedag Field, the Keshband defeat the Saryhorde (411 dead) and sack Bortedag a third time.
  * 911: The Saryhorde and the Keshband come to terms."""

ALLOWED_NAMES = """- Factions: Saryhorde, Keshband
- Rulers: Batu (Saryhorde), Tsetseg the Second (Keshband), Chuluun (Saryhorde ruler, fell in 901)
- Places: Bortebulak (Saryhorde capital), Penpike (Keshband capital), Bortedag (a Saryhorde town), Bortedag Field, Steingrube Field
- Deity: Bayan the Unseen Weaver"""

USER_V3 = """TASK:
Write a biased chronicle passage about the war between {side} and {other}.
- Perspective: You are an unnamed scribe loyal to {side}.
- Bias: Praise the courage and righteousness of {side}. Blame {other} for the conflict and portray their victories as dishonorable, costly, or temporary—WITHOUT changing any outcome, year, or death count.

ALLOWED NAMES (Use exact spelling, use NO other proper nouns):
{allowed}

FACT SHEET:
{facts}

Write the chronicle passage now:"""

# The colleague's settings for Gemma 4 E4B; used for both models so the comparison stays fair.
SAMPLING = {"temperature": 0.6, "top_p": 0.85, "min_p": 0.05, "repeat_penalty": 1.08}


def chat(server, messages, max_tokens, sampling=SAMPLING):
    body = {"messages": messages, "max_tokens": max_tokens, **sampling,
            "chat_template_kwargs": {"enable_thinking": False}}
    import urllib.request
    req = urllib.request.Request(f"{server}/v1/chat/completions", data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json"})
    started = time.time()
    with urllib.request.urlopen(req, timeout=1800) as resp:
        answer = json.loads(resp.read())
    timings = answer.get("timings", {})
    return answer["choices"][0]["message"]["content"].split("</think>")[-1].strip(), {
        "wall_s": round(time.time() - started, 2), "prompt_tokens": timings.get("prompt_n"),
        "gen_tokens": timings.get("predicted_n"), "prompt_tps": timings.get("prompt_per_second"),
        "gen_tps": timings.get("predicted_per_second"),
    }


def write_book(server, sheet, side, other, attempts=3):
    user = USER_V3.format(side=side, other=other, allowed=ALLOWED_NAMES, facts=WAR_FACTS)
    history = []
    for attempt in range(1, attempts + 1):
        content = user if not history else user + "\n\nAVOID\n" + validator.feedback(history[-1]["problems"])
        messages = [{"role": "system", "content": SYSTEM_V3}, {"role": "user", "content": content}]
        text, timing = chat(server, messages, max_tokens=400)
        problems = validator.check_english(text, sheet)
        history.append({"attempt": attempt, "text": text, "problems": problems, "timing": timing})
        if not problems:
            break
    return history


def translate(server, text):
    messages = [{"role": "user", "content": TRANSLATE.format(text=text)}]
    return chat(server, messages, max_tokens=700, sampling={"temperature": 0.2, "top_p": 0.9})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", required=True)
    ap.add_argument("--server", default="http://127.0.0.1:8080")
    ap.add_argument("--sheets", default="data/genesis-11753242.jsonl")
    args = ap.parse_args()

    sheets = {json.loads(l)["ref"]: json.loads(l) for l in open(args.sheets, encoding="utf-8")}
    sheet = sheets[WAR_REF]
    with open(f"results/{args.label}-book-v3.jsonl", "a", encoding="utf-8") as out:
        for side, other in (("Saryhorde", "Keshband"), ("Keshband", "Saryhorde")):
            history = write_book(args.server, sheet, side, other)
            text = history[-1]["text"]
            de, t_de = translate(args.server, text)
            record = {
                "label": args.label, "side": side, "other": other, "text": text,
                "passed": not history[-1]["problems"], "attempts": history,
                "de": de, "de_timing": t_de, "de_problems": validator.check_translation(text, de),
            }
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
            out.flush()
            print(f"{args.label} {side}: tries={len(history)} ok={record['passed']} "
                  f"write={history[-1]['timing']['wall_s']}s de={t_de['wall_s']}s", flush=True)


if __name__ == "__main__":
    main()
