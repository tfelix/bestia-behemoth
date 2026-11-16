"""Aggregate generation speed with N requests at once. Generation on a CPU waits on memory, so a batch of
sequences shares each pass over the weights."""
import json, sys, time, os
from concurrent.futures import ThreadPoolExecutor
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import prose

sheets = {json.loads(l)["ref"]: json.loads(l) for l in open(sys.argv[1])}
n = int(sys.argv[2])
plan = json.load(open(sys.argv[3]))

def one(job):
    messages = [{"role": "system", "content": prose.SYSTEM}, {"role": "user", "content": prose.prompt_for(job, sheets[job["ref"]])}]
    return prose.chat("http://127.0.0.1:8080", messages, max_tokens=128, temperature=0.7)[1]

started = time.time()
with ThreadPoolExecutor(n) as pool:
    results = list(pool.map(one, plan))
wall = time.time() - started
tokens = sum(r["gen_tokens"] for r in results)
prompt = sum(r["prompt_tokens"] for r in results)
print(json.dumps({"parallel": n, "wall_s": round(wall, 1), "prompt_tokens": prompt, "gen_tokens": tokens,
                  "texts_per_min": round(len(plan) / wall * 60, 2), "gen_tps": round(tokens / wall, 2)}))
