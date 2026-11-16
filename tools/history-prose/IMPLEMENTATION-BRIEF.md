# Lore prose generation: implementation brief

This brief is for an engineer or LLM who builds the lore generation feature in bestia-behemoth. It holds everything
the spike of 2026-11-16 learned that matters for good results. Prompts and settings below are copied from the
spike code and were measured as written. Where something was **not** tested, the text says so.

Spike code: branch `spike/history-prose`, folder `tools/history-prose/` (`prompt_v3.py` is the best prompt builder,
`validator.py` the validator, `book_prompt_v3.py` the German prompt). Raw results: `tools/history-prose/results/`.
Reading set with every text: `docs/history-prose-samples/index.html`.

## 1. What the feature does

Once per world, after world generation, a background job on the zone server writes short lore texts from the world's
chronicle, in every client language. Players find them in the world. The texts are static. The world is playable
before the job ends.

Text kinds, and how many one world needs (from `./gradlew :worldgen:chronicle -Pgenesis -Pfactsheet`):

| Kind | Subject | Genesis world | Large world (256 cells) |
| --- | --- | ---: | ---: |
| Epitaph | each figure with a grave | 38 | 93 |
| Artifact legend | each artifact | 32 | 63 |
| Hymn | each god | 5 | 7 |
| War chronicle | each war, once per side | 10 | 32 |
| People chronicle | each civ | 4 | 8 |
| Battlefield memorial | each battlefield | 7 | 20 |
| Cult tract | each cult | 3 | 10 |
| World boss legend | each world boss | 3 | 4 |
| House chronicle | each dynasty | 7 | 13 |
| **Total** | | **109** | **250** |

## 2. Runtime: llama.cpp server as a sidecar

Do **not** run the model inside the JVM. Jlama 0.8.4 (the only pure-JVM option tested) read prompts at half the speed,
took 5 minutes per text, held 10 GB in the heap, and cannot load Gemma 4, Qwen3 or Ministral 3 (no release since
January 2025).

Run llama.cpp's server in its own container next to the zone. The zone calls it over HTTP with Spring's `RestClient`.

- Image: `ghcr.io/ggml-org/llama.cpp:server` (CPU build). Tested: digest
  `sha256:7751a6c0dc3d019c231d42678abbb0fac1a13a24c17c956109ed27533139f280`, llama.cpp build 12429. Pin a digest.
- Endpoint: `POST /v1/chat/completions` (OpenAI style). Health: `GET /health`.
- The response has a `timings` object (`prompt_n`, `prompt_per_second`, `predicted_n`, `predicted_per_second`). Log it.

Server flags (each one was measured):

```sh
llama-server -m /models/gemma-4-E4B-it-Q4_K_M.gguf \
  -c 4096 -np 1 \
  -t 6 -tb 10 \
  --no-repack --cache-ram 0 \
  --host 0.0.0.0 --port 8080
```

- `-t 6 -tb 10`: 6 threads for generation, 10 for reading the prompt. On the test CPU (2 performance + 8 efficiency
  cores) more generation threads made generation slower. **Re-measure on the target server CPU.**
- `-np 1`: one request at a time. 2 or 4 parallel slots did **not** raise throughput on a CPU; the cores compute, they
  do not wait on memory.
- `--no-repack`: by default llama.cpp keeps a repacked copy of the weights next to the memory-mapped file. Same speed
  without it, 2-3 GB less RAM.
- `--cache-ram 0`: by default the server keeps up to 8 GB of old prompts for reuse. Every lore prompt is different, so
  that cache only grows. With both flags Gemma 4 E4B peaked at **5.0 GB RSS** over 12 texts (default flags: 11.3 GB
  over a world).
- Give the container a CPU set and a memory limit (`--cpuset-cpus`, `--memory`) so the game tick keeps its cores.

## 3. Model: Gemma 4 E4B-it, Q4_K_M

- File: `gemma-4-E4B-it-Q4_K_M.gguf`, 4.98 GB, from `unsloth/gemma-4-E4B-it-GGUF` on Hugging Face. Google publishes
  no GGUF of its own. License: Apache-2.0 (commercial use allowed).
- Fallback: Qwen3-8B `Q4_K_M` (`Qwen/Qwen3-8B-GGUF`, Apache-2.0). As reliable with the original prompt, but **worse
  with the v3 prompt** (read a ruler's name as a town) and 3 times slower. For Qwen3 send
  `"chat_template_kwargs": {"enable_thinking": false}`, or it thinks for minutes first. Sending it to Gemma is harmless.
- Rejected, do not retry: Ministral-3-8B (best voice, moves facts around), Qwen3-4B (lowercase output, list-like),
  Ministral-3-3B (garbles years and counts), Mistral-7B v0.3, Llama 3.1 8B and its fine-tunes (license burden;
  Stheno is CC-BY-NC), roleplay or "creative" fine-tunes in general (they embellish, which means inventing names).

Measured speed of Gemma 4 E4B on the test CPU (Intel i7-1355U, 15 W laptop chip, AVX2 + AVX-VNNI):

| | Value |
| --- | ---: |
| Prompt reading | 36-45 tokens/s |
| Generation | 5.6-6.9 tokens/s |
| Epitaph (v3 prompt, ~470 prompt tokens, ~55 generated) | 23 s |
| Legend (~500 / ~120) | 34 s |
| Hymn (~560 / ~80) | 33 s |
| War chronicle (~840 / ~210) | 62 s |
| Mean over the reading set, v3 prompt | 34 s per text |
| Whole genesis world, original (shorter) prompt | 41 min for 107 texts |
| Whole genesis world, v3 prompt | not run; about 1 h expected |
| Large world, v3 prompt | not run; about 2.3 h expected |

German translation with the same model takes about as long as writing, so each language adds the same time again.

## 4. The v3 prompt

Validated: 12 of 12 texts valid at the first try, the best quality of the spike (rubric 4.8 of 5). Two messages:
a system message, and a user message built from the fact data.

### 4.1 System message (verbatim)

```text
You are an in-world writer of short lore texts for a fantasy game: chronicles, grave stones, legends, hymns.
Your highest priority is strict factual accuracy based ONLY on the provided FACT SHEET.

STRICT VALIDATOR RULES:
1. ALLOWED PROPER NOUNS ONLY: You may ONLY use proper nouns listed in the ALLOWED NAMES list. Never invent names of people, places, gods, ranks, or titles (do NOT use words like King, Lord, General, or Emperor unless they appear in the ALLOWED NAMES list).
2. NUMBERS & YEARS: Write all years and numbers strictly as digits (e.g., 901, 406), never as words. Use ONLY numbers present in the FACT SHEET.
3. ALLOWED CREATIVITY: You may describe weather, emotions, landscape, and unnamed soldiers/crowds to create atmosphere.
4. STYLE: Write in simple, archaic prose. Avoid modern words.
5. OUTPUT FORMAT: Output ONLY the final text, in the length and shape the TASK asks. Do NOT include a title, intro, notes, or markdown formatting.
```

### 4.2 User message layout (verbatim)

```text
TASK:
{task}

ALLOWED NAMES (Use exact spelling, use NO other proper nouns):
{allowed}

FACT SHEET:
{facts}

Write the text now:
```

The order follows a colleague's advice for small models: hard rules in the system message, the task first in the
user message, then names and facts, and the instruction to write last. It was tested only as a whole (v3), not
against other orders.

### 4.3 Tasks (verbatim; tested for these four kinds)

War chronicle (`{side}` and `{other}` are the two peoples, e.g. "Saryhorde"; one text per side):

```text
Write a biased chronicle passage about the war between the {side} and the {other}. 1 to 2 paragraphs, 100 to 180 words.
- Perspective: You are an unnamed scribe loyal to the {side}.
- Bias: Praise the courage and righteousness of the {side}. Blame the {other} for the cruelty and the losses of the war, and portray their victories as dishonorable, costly, or temporary. Keep who declared the war as the FACT SHEET says.
- Name every battle with its year, its battlefield and its death count, exactly as the FACT SHEET gives them.
```

Epitaph:

```text
Write the epitaph {framing} of {name}. 2 to 5 short lines, 20 to 50 words in total. Name the dead, the years of birth and death, and one deed or tie from the FACT SHEET. Solemn tone.
```

Artifact legend:

```text
Write the legend of {name} as {framing}. One paragraph, 80 to 130 words. Follow the events in order: who made it, of what, where, what became of it, and where it is now. Start with the subject itself, not with a call to listen.
```

Hymn (`{name}` is the god's name **in the singing people's culture**; a god has one name per culture):

```text
Write a hymn to {name}, the god of {domain}, as the {people} sing it. 6 to 10 short lines. Speak to the god by the name the {people} use. Draw on the god's domain and on what the FACT SHEET says about temples, cults or events.
```

Framings, picked by `crc32(entity ref) % count`, so a text keeps its framing on a rerun:

- legend: "an old storyteller tells it at the fire", "a temple keeper writes it in the record of relics",
  "a traveller notes it down after hearing it in an inn", "a scholar writes it in a catalogue of famous things"
- epitaph: "carved on the grave", "cut into the stone above the barrow door", "written on a weathered marker by the
  grave"

Why framings: with one framing, Gemma opened 31 of 32 legends with "Hark, and lend thine ear". With four framings and
"start with the subject itself", none did.

**Not yet in v3 form (untested):** people chronicle, house chronicle, battlefield memorial, cult tract, world boss
legend. In the world run they used the original prompt and were all valid, but not with v3. Write their v3 tasks in
the same style, starting from these original task lines:

- people: "Write the opening passage of a chronicle of the {name}, as one of their own scholars wrote it. One or two
  paragraphs, 100 to 180 words. Proud of their past, honest about the years."
- house: "Write a passage from the family book of {name}. One paragraph, 80 to 150 words. Name the rulers in order
  with their years, and tell of the house's quarrels and ties."
- memorial: "Write the inscription on a memorial stone at {name}. 30 to 70 words. Name who fought here, who won, and
  how many fell."
- tract: "Write a passage from a tract of {name}, written by a believer. One paragraph, 80 to 130 words. Tell how the
  faith began, where it spread, and what its followers must do."
- beast: "Write the legend of {name} as a frightened villager tells it. One paragraph, 80 to 130 words. Follow the
  events in order and end with where it is now, as far as anyone knows."

### 4.4 Sampling

```json
{"temperature": 0.6, "top_p": 0.85, "min_p": 0.05, "repeat_penalty": 1.08, "max_tokens": 400}
```

Higher temperature (0.8+) raises the rate of invented titles and names. `repeat_penalty` keeps the three similar
battles of a war from getting the same sentence.

## 5. Building ALLOWED NAMES and FACT SHEET

This is what made v3 work. A 4B model cannot guess which "Chuluun" an event means (the genesis world has four) or
what "Bortedag" is. The prompt must tell it.

### 5.1 ALLOWED NAMES

One line per proper noun the text may use, with what it is:

```text
- Batu (ruler of the Saryhorde, 901-966)
- Bayan the Unseen Weaver (god of fate, as the Saryhorde, the Keshband and the Bortekhai call it)
- Bortebulak (capital of the Saryhorde)
- Bortedag (a town)
- Bortedag Battlefield (a battlefield near Bortedag)
- Chuluun (ruler of the Saryhorde, 841-901)
- Keshband (a people, pastoral, since 716)
- Penpike (capital of the Keshband)
- Saryhorde (a people, pastoral, since 1)
- Steingrube Battlefield (a battlefield near Steingrube)
- Tsetseg the Second (ruler of the Keshband, 886-951)
```

Rules:

- Include every entity the facts mention: the subject, every event's actors, places, battlefields, gods, artifacts,
  houses, cults, graves.
- Describe each in a few words: role and people and life years for a figure; "capital of X" or "a town" for a place;
  "a battlefield near X"; "god of DOMAIN, as the X call it"; "a crown of wrought iron, made 716" for an artifact;
  "grave of X" for a barrow.
- **Resolve every name to one entity.** The spike resolved duplicates by the years of the events a name appears in.
  That is a stopgap. Build the list from the chronicle's **actor refs** on each event (`HistoryEvent.actors`), not
  from names. A barrow name is shared by every grave in it: describe only the subject's grave.
- If two entities still share a name in one text, keep both with their descriptions; the model then tells them apart.
- A name that an event line uses but no entity has (e.g. a town's name before a conquest) still goes in the list.
  With PR #419, settlements keep `formerNames`; describe them as "former name of X, until YEAR".

### 5.2 FACT SHEET

Plain lines, no JSON, no internal ids or keys:

```text
- Subject: the war between the Saryhorde and the Keshband, 901-911
- sides: people: Saryhorde, capital: Bortebulak, battlesWon: 0, patronGod: Bayan the Unseen Weaver, rulerAtStart: Batu
- sides: people: Keshband, capital: Penpike, battlesWon: 3, patronGod: Bayan the Unseen Weaver, rulerAtStart: Tsetseg the Second
- declared: 901
- peace: 911
- Chronology of events:
  * 901: The Saryhorde go to war with the Keshband.
  * 901: The Keshband defeat the Saryhorde near Bortedag, on Bortedag Battlefield, 406 dead.
  * 901: Chuluun falls in battle.
  * 901: Bortedag is sacked by the Keshband.
  * 906: The Keshband defeat the Saryhorde near Bortedag, on Steingrube Battlefield, 408 dead.
  ...
  * 911: The Saryhorde and the Keshband come to terms.
```

Rules:

- Never put an internal title like "the war of Saryhorde and Keshband, 901" in the sheet; small models copy it into
  the text. Write the subject as a sentence.
- Put each battle's **battlefield into its event line**. The event text says "near Bortedag", but the 906 battle was
  fought on Steingrube Battlefield. Without the field in the line, models (and people) put it in the wrong place.
- Do not send cause chains ("because: ..."). They repeat earlier lines and cost a third of the prompt.
- Keep only what a text can use. A civ's own event list runs to hundreds of famines; the spike kept events with
  importance >= 55 for civ sheets.
- Use the figure's sex (PR #420, `FigureRecord.sex`) in the figure's ALLOWED NAMES line, e.g. "ruler of the
  Keshband, a woman, 886-951". Untested; without it, models guess, mostly "he".

## 6. Validator (keep it in the loop)

Every text is checked against what the prompt allowed (the sheet plus the ALLOWED NAMES). A failing text is written
again, at most 3 times in all. Gemma 4 E4B with v3 needed no retry in 12 texts; with the original prompt, 1 retry in
107. Port `validator.py`. What it checks:

1. **Names.** Find capitalised phrases (words joined by spaces, "of", "the", "of the"; **never across a line break**,
   epitaph lines start with names). Skip a sentence's first word when it is a common English word (a dictionary
   lookup) and style words ("O", "Thou", "Thy", "Hail", "Amen", "Twas", ...). The phrase must occur in the allowed
   text. A phrase of two allowed names joined by "of" passes ("Temujin of Karaobo"). A title before a name ("King
   Batu") fails on its own.
2. **Numbers.** Every digit sequence must occur in the sheet. Also numbers in words: "four hundred and eight" is 408,
   "five seventy-one" is a spoken year, 571. Small counts up to 12 ("the two peoples") are style and pass.
3. **Letter case.** A sheet name written in other case ("keshband") fails. A sentence that starts in lowercase fails.
4. **Retry without the bad text.** The retry prompt is the same prompt plus a block:

   ```text
   AVOID
   "Eldoria" is not a name in the fact sheet. Use only names from the sheet. The number 952 is not in the fact sheet. Use only years and numbers from the sheet.
   ```

   Do **not** send the rejected text back as an assistant turn. Small models then copy it word for word.

What the validator **cannot** catch: a true name in the wrong place (a sacking moved from Bortedag to the capital, a
count given to the wrong side). Both names and numbers are allowed, so the text passes. The v3 prompt's names list and
battle lines made these errors rare; they did not make them impossible. Consider a cheap second check later.

Missing today, add it: **a check for phrases repeated across one world's texts.** After the framings, "a silent
witness to the ages" still stood in 16 of 32 legends. Count 4-6 word sequences over the world's finished texts; feed
the common ones back as "AVOID the phrase ..." on the next texts of that kind.

## 7. German (and later languages)

Use the **same model**, not a separate translation model. Argos Translate (CTranslate2) was 100 times faster but got
lore words wrong ("barrow" became "Scheune", barn, and "Leiche", corpse; "sacked" became "entlassen", dismissed),
mixed translated and untranslated names, lowercased whole sentences, and its model files state no license.

Translation prompt (verbatim, the whole user message, no system message; temperature 0.2, top_p 0.9, max_tokens 700):

```text
Du bist ein professioneller deutscher Videospiel-Lokalisierer für ein Fantasy-MMORPG. 
Übertrage den folgenden englischen Chronik-Text in natürliches, atmosphärisches und idiomatisches Deutsch.

Regeln:
1. Übersetze NICHT Wort für Wort, sondern formuliere so, wie es in einem deutschen Fantasy-Roman stehen würde.
2. Verwende bei Fraktionen und Gruppen immer passende deutsche Artikel (z. B. "die Saryhorde" oder "die Keshband").
3. Verändere KEINE Eigennamen, Ortsnamen oder Zahlen (z.B. 901, Bortedag).
4. Gib ausschließlich den fertigen deutschen Text ohne Kommentare aus.

Englischer Text:
"{text}"
```

With it, Gemma writes German epithets ("Bayan dem Unsichtbaren Weber") and consistent articles ("die Keshband"), and
even rhymes hymns. Its slips are grammar: a split verb not split ("heimsuchten" for "suchten heim"), a missing
genitive ("Tugend Batu"), a wrong case after "von". Once, a count came out garbled ("vierhundertelfund und elf").

To do (not tested):

- **Give the translator a glossary.** PR #421 renders every name in German (`NameLocale.DE`): "Kunimund der
  Widerwillige", "das Schlachtfeld von Bortedag", "Haus Tregrube". Add a block to the prompt: "Use these German names
  exactly: Kunimund the Reluctant = Kunimund der Widerwillige; ..." Without it, the model translates some epithets and leaves
  others in English.
- **Validate the German text.** Every invented name (a word no English dictionary knows) and every number of the
  English text must appear in the German one; glossary names must appear in their German form. `check_translation`
  in `validator.py` does the first two.
- Keep years and counts as digits in the English source. A spelled-out year cannot be checked after translation.

## 8. The job

- Runs once after world generation, as a background job. The world is playable before it ends.
- **The table is the queue.** Plan every text the world needs (section 1). The work left is the plan minus the rows
  already stored. So the job can stop at any time (restart, deploy) and go on.
- English first, for all texts. Then each other locale, as a second pass with the same model.
- One request at a time to the sidecar. Retry a failed HTTP call; a validator failure is a retry of the text, up to
  3 tries in all. Suggested (not built): do not store a text that still fails; leave it missing, log it, and let the
  client show a short fallback line.
- Log per text: kind, entity ref, attempts, prompt and generated tokens, seconds, validator findings.

## 9. Storage and delivery (designed, not built)

- `world_chronicle`: one row per world. `chronicle_hash` char(64) primary key (SHA-256 of the serialised
  chronicle), `format` int, `pipeline_version` bigint, `payload` deflated (`@Lob`, `Length.LONG32`, as in
  `PersistedChunkEdit`), `created_at`. The genesis world's fact data is 123 KB raw, 16 KB deflated.
- `world_lore_text`: primary key (`entity_ref` varchar(32), `text_kind` varchar(16), `variant` varchar(32): the side
  a war chronicle is told by, else empty, `locale` varchar(8), `chronicle_hash` char(64)); plus `body` text, `model`
  varchar(64), `attempts` int, `created_at`.
- Both are cleared by one `WorldScopedData` bean, like `TreasureWipe`. The zone runs Hibernate `ddl-auto: update`, so
  no Flyway file.
- Delivery **on request**: `RequestLoreTextCMSG(entity_ref, text_kind, variant)` → `LoreTextSMSG(..., locale, body)`,
  in the pattern of `RequestCompendiumCMSG`. Not a bundle: a legend says where a relic lies, so a bundle would hand
  every quest hook to the client cache; on request also works while the job still runs. One language is only 43 KB
  for a genesis world.
- Where players read them: war chronicles and people/house chronicles as readable book items (the item carries only
  the reference); epitaphs on grave props at barrows; legends when examining a relic; hymns at temples.

## 10. Chronicle changes this depends on

Open draft PRs (stacked) from 2026-11-16:

- bestia-behemoth #419: settlements keep `formerNames` (seed, culture, since, until) and `nameCulture`. Use them for
  "former name" lines and to render a town's name as the event lines did.
- #420: `FigureRecord.sex` from the given name; `Names.deitySex(seed, culture)` for gods.
- #421: battlefields named "X Battlefield"; `NameLocale` with `EN` and `DE` for every name.
- bestia-docs #115: `server/name-generation.md`.

Still missing for best results:

- **Actor refs in the prompt builder.** Build ALLOWED NAMES from `HistoryEvent.actors`, not by matching names.
- **Name collisions stay.** Four figures called Chuluun, two battlefields called "Bortedag Battlefield" (901 and 911)
  are intended. The ALLOWED NAMES descriptions must tell them apart (life years, people, which battle).

## 11. Checklist for the first build

1. Sidecar with the flags of section 2, pinned image digest, CPU set and memory limit. Measure tokens/s on the real
   server CPU with `llama-bench` (in the image: `/app/llama bench -m ... -p 512 -n 128 -t 6 -d 0,800`).
2. Prompt builder from the chronicle: sections 4 and 5, actor refs, framings, sex, former names.
3. v3 tasks for the five untested kinds; run each kind once over a genesis world and read the texts.
4. Validator port, with the retry block and the repeated-phrase check.
5. German pass with the localisation prompt and the `NameLocale.DE` glossary; validate it.
6. Storage, wipe, job queue, delivery message, fallback lines.
7. Run a whole genesis world end to end; expect about 1 h for English and 1 h per extra language on a CPU like the
   test machine.
