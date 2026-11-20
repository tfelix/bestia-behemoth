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

So the default model stays. The German errors come from the translation step, not from the size of the model.
