"""Prompt and generation speed of the running server, on a fixed prompt. Prints one JSON line."""
import json, sys, time, urllib.request
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import prose

sheets = {json.loads(l)["ref"]: json.loads(l) for l in open(sys.argv[1])}
job = {"ref": "war#409", "type": "book", "args": {"side": "Saryhorde", "other": "Keshband"}}
messages = [{"role": "system", "content": prose.SYSTEM}, {"role": "user", "content": prose.prompt_for(job, sheets[job["ref"]])}]
runs = []
for i in range(2):
    # A different first word each run, so the server cannot reuse its prompt cache.
    messages[1]["content"] = f"Run {i}.\n" + messages[1]["content"]
    text, t = prose.chat("http://127.0.0.1:8080", messages, max_tokens=128, temperature=0.7)
    runs.append(t)
print(json.dumps({"label": sys.argv[2], "pp_tps": round(sum(r["prompt_tps"] for r in runs) / 2, 1),
                  "tg_tps": round(sum(r["gen_tps"] for r in runs) / 2, 2), "prompt_tokens": runs[0]["prompt_tokens"]}))
