"""The war chronicles again, with the user's revised book prompt and German localisation prompt.

Writes both sides of the war with the revised prompt (same fact sheet, same validator and retries), then has
the same model translate each new book twice: with the revised German prompt and with the old one, so the
effect of the translation prompt shows on the same source text.

    python3 book_prompt_v2.py --label gemma4-e4b --server http://127.0.0.1:8080
"""
import argparse
import json
import time

import prose
import translate
import validator

WAR_REF = "war#409"

SYSTEM_V2 = "You are a writer for a fantasy MMORPG."

TASK_V2 = (
    "You write a passage from a chronicle about the war between the {side} and the {other}. "
    "One or two paragraphs, 100 to 180 words. The scribe is biased: praises the {side}, blames the {other}, "
    "and explains away every defeat of the {side}. But every event, winner, death count and year must stay as "
    "the fact sheet gives it. Do not give the scribe a name. Do not write down numbers, use the digits "
    "(e.g. 901). Do not invent new facts or names."
)

TRANSLATE_V2 = """Du bist ein professioneller deutscher Videospiel-Lokalisierer für ein Fantasy-MMORPG.
Übertrage den folgenden englischen Chronik-Text in natürliches, atmosphärisches und idiomatisches Deutsch.

Regeln:
1. Übersetze NICHT Wort für Wort, sondern formuliere so, wie es in einem deutschen Fantasy-Roman stehen würde.
2. Verwende bei Fraktionen und Gruppen immer passende deutsche Artikel (z. B. "die Saryhorde" oder "die Keshband").
3. Verändere KEINE Eigennamen, Ortsnamen oder Zahlen (z.B. 901, Bortedag).
4. Gib ausschließlich den fertigen deutschen Text ohne Kommentare aus.

Englischer Text:
"{text}"
"""


def write_book(server, sheet, side, other, attempts=3):
    facts = "\n".join(prose.compact(sheet))
    task = TASK_V2.format(side=side, other=other)
    history = []
    for attempt in range(1, attempts + 1):
        user = f"FACT SHEET\n{facts}\n\nTASK\n{task}"
        if history:
            user += "\n\nAVOID\n" + validator.feedback(history[-1]["problems"])
        messages = [{"role": "system", "content": SYSTEM_V2}, {"role": "user", "content": user}]
        text, timing = prose.chat(server, messages, max_tokens=400, temperature=0.7)
        problems = validator.check_english(text, sheet)
        history.append({"attempt": attempt, "text": text, "problems": problems, "timing": timing})
        if not problems:
            break
    return history


def translate_v2(server, text):
    messages = [{"role": "user", "content": TRANSLATE_V2.format(text=text)}]
    return prose.chat(server, messages, max_tokens=700, temperature=0.2)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", required=True)
    ap.add_argument("--server", default="http://127.0.0.1:8080")
    ap.add_argument("--sheets", default="data/genesis-11753242.jsonl")
    args = ap.parse_args()

    sheets = {json.loads(l)["ref"]: json.loads(l) for l in open(args.sheets, encoding="utf-8")}
    sheet = sheets[WAR_REF]
    a, b = (s["people"] for s in sheet["sides"])
    with open(f"results/{args.label}-book-v2.jsonl", "a", encoding="utf-8") as out:
        for side, other in ((a, b), (b, a)):
            started = time.time()
            history = write_book(args.server, sheet, side, other)
            text = history[-1]["text"]
            de_new, t_new = translate_v2(args.server, text)
            de_old, t_old = translate.llm_translate(args.server, text)
            record = {
                "label": args.label, "side": side, "other": other, "text": text,
                "passed": not history[-1]["problems"], "attempts": history,
                "de_v2": de_new, "de_v2_timing": t_new, "de_v2_problems": validator.check_translation(text, de_new),
                "de_v1": de_old, "de_v1_timing": t_old, "de_v1_problems": validator.check_translation(text, de_old),
                "wall_s": round(time.time() - started, 1),
            }
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
            out.flush()
            print(f"{args.label} {side}: tries={len(history)} ok={record['passed']} "
                  f"write={history[-1]['timing']['wall_s']}s de_v2={t_new['wall_s']}s de_v1={t_old['wall_s']}s", flush=True)


if __name__ == "__main__":
    main()
