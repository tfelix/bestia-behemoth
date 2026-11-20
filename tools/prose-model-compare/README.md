# Model comparison for the news texts

`compare.sh <outdir> [runs]` writes the 40 news texts (20 English, 20 German) once per model. The texts of each
run are saved here. Run on 2026-11-19/20 on the development machine, CPU only, 6 threads.

| Run                             | Seconds, 40 texts | English, avg | German, avg | First try |
| ------------------------------- | ----------------- | ------------ | ----------- | --------- |
| E4B Q4_K_M, repeat penalty 1.08 | 703               | 20 s         | 15 s        | 40 / 40   |
| E4B QAT Q4_0, repeat penalty 1  | 934               | 28 s         | 19 s        | 40 / 40   |
| 12B QAT Q4_0, repeat penalty 1  | 3542              | 110 s        | 67 s        | 40 / 40   |

What reading them showed:

- **English** is free of spelling errors in all three. 12B is the cleanest and stays closest to the facts, but
  its 20 texts are nearly the same text: the voices are lost. Both E4B runs make up small things (a fire, kin in
  the town) and E4B QAT repeats one flourish ("before the silence grows too deep") three times.
- **German** is not better with either model. E4B QAT: "des Besties", "berichteten vom Mord", "zerfetzt",
  "zu {town}". 12B: "Werdessen Täter sind", "dem Geschehnen", "herabgelegt", "suchten das Grab", and line breaks
  inside five texts.

The German errors come from the translation step, not from the size of the model.

## German written at once

Run `e4b-qat-direct`: E4B QAT, repeat penalty 1.05, each text four ways. Saved in
`e4b-qat-q4_0-rp1.05-direct-german.txt`.

| Way                                              | Avg per text | Defects in 20 texts                                           |
| ------------------------------------------------ | ------------ | ------------------------------------------------------------- |
| `de`: English text, then translated              | 23 s         | 3 grammar errors, about 8 wrong or odd words, a fire made up  |
| `de-direct`: English brief, "write in German"    | 35 s         | no grammar error, 3 odd sentences, `{town}` misused once      |
| `de-german-brief`: rules, task, facts in German  | 29 s         | 1 grammar error, "zu {town}" 12 times, `{town}` misused once  |

Writing German at once from the English brief is clearly the best. It costs about 20 % more time for both
languages together (34 s + 35 s instead of 34 s + 23 s). The validator cannot check it yet: it finds made-up
names by words that are not English.

## What was built from this

The text job now writes every language from the brief, and the default is E4B QAT with a repeat penalty of 1.05.
`test-world-e4b-qat-rp1.05.txt` holds all 74 texts of the test world written that way (graves, quest texts, news;
English and German): 74 written, none rejected, 2 second tries, about 28 minutes.

What still goes wrong in the German texts, and no check can see:

- A name bent out of shape: "Keshschar" for Keshband.
- A name in the wrong case after "von": "von Ulrica die Gesetzgeberin".
- `{town}` used for the wrong town in 2 of 20 news texts ("nicht weit von {town} getötet").
- Places that have no German form stay English: "Pale Hollow".
