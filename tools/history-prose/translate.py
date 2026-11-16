"""Translates generated texts from English to German, two ways, and checks that names survive.

- llm:   the same chat endpoint that wrote the text.
- argos: the Argos en->de package, run with CTranslate2 directly. Argos's own Python package pulls in
         stanza and torch only to split sentences, so this splits sentences itself.

    python3 translate.py --in results/qwen3-8b-reading.jsonl --out results/qwen3-8b-de.jsonl \
        --how llm --server http://127.0.0.1:8080 [--only book]
"""
import argparse
import json
import os
import re
import time
import zipfile

import validator

LLM_SYSTEM = """You translate texts from a fantasy game from English into German.
Rules:
- Keep every proper name exactly as it is: people, places, peoples, gods, houses, artifacts. Do not translate or decline them.
- Keep every number and year as digits.
- Keep the tone and the old, simple style. A grave stone stays a grave stone, a hymn stays a hymn.
- Output only the German text."""


def llm_translate(server, text):
    import prose
    messages = [{"role": "system", "content": LLM_SYSTEM}, {"role": "user", "content": text}]
    out, timing = prose.chat(server, messages, max_tokens=600, temperature=0.2)
    return out, timing


class Argos:
    def __init__(self, package, workdir):
        import ctranslate2
        import sentencepiece

        target = os.path.join(workdir, "argos-en-de")
        if not os.path.isdir(target):
            with zipfile.ZipFile(package) as z:
                z.extractall(target)
        root = os.path.join(target, os.listdir(target)[0])
        self.translator = ctranslate2.Translator(os.path.join(root, "model"), device="cpu", inter_threads=1,
                                                 intra_threads=6)
        self.sp = sentencepiece.SentencePieceProcessor(model_file=os.path.join(root, "sentencepiece.model"))

    def translate(self, text):
        started = time.time()
        out_lines = []
        for line in text.split("\n"):
            sentences = [s for s in re.split(r"(?<=[.!?;])\s+", line.strip()) if s]
            if not sentences:
                out_lines.append("")
                continue
            tokens = [self.sp.encode(s, out_type=str) for s in sentences]
            results = self.translator.translate_batch(tokens, beam_size=4)
            out_lines.append(" ".join(self.sp.decode(r.hypotheses[0]) for r in results))
        return "\n".join(out_lines), {"wall_s": round(time.time() - started, 2)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="inp", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--how", choices=["llm", "argos"], required=True)
    ap.add_argument("--server", default="http://127.0.0.1:8080")
    ap.add_argument("--argos", default=os.path.expanduser("~/llm-spike/argos/translate-en_de-1_3.argosmodel"))
    ap.add_argument("--only", default="")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--any", action="store_true", help="also rejected texts, for a model with no valid one")
    args = ap.parse_args()

    argos = Argos(args.argos, os.path.expanduser("~/llm-spike/argos")) if args.how == "argos" else None
    records = [json.loads(l) for l in open(args.inp, encoding="utf-8")]
    records = [r for r in records if (r["passed"] or args.any) and (not args.only or r["job"]["type"] == args.only)]
    if args.limit:
        records = records[: args.limit]

    with open(args.out, "a", encoding="utf-8") as out:
        for r in records:
            if argos:
                german, timing = argos.translate(r["text"])
            else:
                german, timing = llm_translate(args.server, r["text"])
            problems = validator.check_translation(r["text"], german)
            out.write(json.dumps({
                "label": r["label"], "how": args.how, "job": r["job"], "name": r["name"], "en": r["text"],
                "de": german, "problems": problems, "timing": timing,
            }, ensure_ascii=False) + "\n")
            out.flush()
            print(f"{args.how:5} {r['job']['type']:8} {r['name'][:40]:40} problems={len(problems)} "
                  f"{timing.get('wall_s')}s", flush=True)


if __name__ == "__main__":
    main()
