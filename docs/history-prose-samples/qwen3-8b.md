# Reading set: qwen3-8b

12 texts. Validator: 11 passed at once, 12 after retries.

## Chronicle book: the war of Saryhorde and Keshband, 901 (told by the Saryhorde)

> The war between the Saryhorde and the Keshband began in the year 901, when the Keshband, under their ruler Tsetseg the Second, struck without warning. Though the Saryhorde had long held their capital, Bortebulak, the Keshband's swift attacks brought great sorrow. Yet the Saryhorde, guided by Bayan the Unseen Weaver, stood firm. The first battle near Bortedag was a bitter loss, but the Saryhorde did not falter. It was the Keshband's arrogance that led to their own undoing. In the years that followed, the Keshband wasted their strength in repeated attacks on Bortedag, a city that would not fall. Though the Saryhorde suffered, they endured, and in the year 911, peace was finally made, though not without great cost.

**Validator:** passed after 1 attempt(s).
- last attempt: 632 prompt tokens at 17.0/s, 188 generated at 4.6/s; whole text 78 s

**German (llm):**

> Der Krieg zwischen der Saryhorde und den Keshband begann im Jahr 901, als die Keshband unter ihrem Herrscher Tsetseg der Zweite ohne Vorankündigung angriffen. Obwohl die Saryhorde seit langer Zeit ihr Hauptstadt Bortebulak beherrschte, brachten die schnellen Angriffe der Keshband große Trauer. Doch die Saryhorde, geleitet von Bayan dem Unbekannten Weber, blieb standhaft. Das erste Gefecht nahe Bortedag war ein bitterer Verlust, doch die Saryhorde zögerte nicht. Es war die Hochmütigkeit der Keshband, die zu ihrem Untergang führte. In den folgenden Jahren verschwendeten die Keshband ihre Kräfte an wiederholte Angriffe auf Bortedag, eine Stadt, die nicht fallen würde. Obwohl die Saryhorde leiden musste, blieben sie standhaft, und im Jahr 911 wurde endlich Frieden geschlossen, doch nicht ohne große Kosten.

**German (argos):**

> Der Krieg zwischen der Saryhorde und dem Keshband begann im Jahr 901, als das Keshband unter ihrem Herrscher Tsetseg dem Zweiten ohne Vorwarnung zuschlug. Obwohl die Saryhorde lange Zeit ihre Hauptstadt, Bortebulak, gehalten hatte, brachten die schnellen Angriffe des Keshbandes großen Kummer. Doch die Saryhorde, geführt von Bayan dem Unsichtbaren Weber, stand fest. Die erste Schlacht in der Nähe von Bortedag war ein bitterer Verlust, aber die Saryhorde schwankte nicht. Es war die Arroganz der Keshband, die zu ihrem eigenen Verderben führte. In den folgenden Jahren verschwendete die Keshband ihre Kraft in wiederholten Angriffen auf Bortedag, eine Stadt, die nicht fallen würde. Obwohl die Saryhorde litt, ertrugen sie, und im Jahr 911 wurde endlich Frieden gemacht, wenn auch nicht ohne große Kosten.

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: war
name: the war of Saryhorde and Keshband, 901
sides:
  - people: Saryhorde
    capital: Bortebulak
    battlesWon: 0
    patronGod: Bayan the Unseen Weaver
    rulerAtStart: Batu
  - people: Keshband
    capital: Penpike
    battlesWon: 3
    patronGod: Bayan the Unseen Weaver
    rulerAtStart: Tsetseg the Second
declared: 901
peace: 911
fields:
  - Bortedag Field
  - Steingrube Field
  - Bortedag Field
events:
  - 901: the Saryhorde go to war with the Keshband
  - 901: the Keshband defeat the Saryhorde near Bortedag, 406 dead
  - 901: Chuluun falls in battle
  - 901: Bortedag is sacked by the Keshband
  - 906: the Keshband defeat the Saryhorde near Bortedag, 408 dead
  - 906: Bortedag is sacked by the Keshband
  - 911: the Keshband defeat the Saryhorde near Bortedag, 411 dead
  - 911: Bortedag is sacked by the Keshband
  - 911: the Saryhorde and the Keshband come to terms

TASK
Write a passage from a chronicle about the war between the Saryhorde and the Keshband. A court scribe of the Saryhorde wrote it. One or two paragraphs, 100 to 180 words. The scribe is biased: praises the Saryhorde, blames the Keshband, and explains away every defeat of the Saryhorde. But every event, winner, death count and year must stay as the fact sheet gives it. Do not give the scribe a name.
```

</details>

## Chronicle book: the war of Saryhorde and Keshband, 901 (told by the Keshband)

> In the year of the Lord’s grace 901, the Saryhorde, under the false rule of Batu, raised their banners against the Keshband. The Keshband, guided by the wisdom of Bayan the Unseen Weaver, stood firm. The first clash near Bortedag saw the Saryhorde’s folly, for they were crushed, with 406 of their men lost. The city of Bortedag fell to the Keshband’s might, a sign of their divine favor. Though the Saryhorde claimed Chuluun in battle, their loss was a mere shadow of their true weakness.  
>
> Year after year, the Keshband pressed forward, their victories undeniable. In 906, the Saryhorde’s stubbornness led to another defeat, Bortedag once more laid bare. The same fate befell the city in 911, a testament to the Keshband’s strength. Though the Saryhorde clung to hope, their defeats were not due to the Keshband’s lack of skill, but their own arrogance and lack of faith. In the end, peace was made, and the Keshband’s name was sung in all lands.

**Validator:** passed after 1 attempt(s).
- last attempt: 105 prompt tokens at 15.0/s, 256 generated at 4.2/s; whole text 68 s

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: war
name: the war of Saryhorde and Keshband, 901
sides:
  - people: Saryhorde
    capital: Bortebulak
    battlesWon: 0
    patronGod: Bayan the Unseen Weaver
    rulerAtStart: Batu
  - people: Keshband
    capital: Penpike
    battlesWon: 3
    patronGod: Bayan the Unseen Weaver
    rulerAtStart: Tsetseg the Second
declared: 901
peace: 911
fields:
  - Bortedag Field
  - Steingrube Field
  - Bortedag Field
events:
  - 901: the Saryhorde go to war with the Keshband
  - 901: the Keshband defeat the Saryhorde near Bortedag, 406 dead
  - 901: Chuluun falls in battle
  - 901: Bortedag is sacked by the Keshband
  - 906: the Keshband defeat the Saryhorde near Bortedag, 408 dead
  - 906: Bortedag is sacked by the Keshband
  - 911: the Keshband defeat the Saryhorde near Bortedag, 411 dead
  - 911: Bortedag is sacked by the Keshband
  - 911: the Saryhorde and the Keshband come to terms

TASK
Write a passage from a chronicle about the war between the Keshband and the Saryhorde. A court scribe of the Keshband wrote it. One or two paragraphs, 100 to 180 words. The scribe is biased: praises the Keshband, blames the Saryhorde, and explains away every defeat of the Keshband. But every event, winner, death count and year must stay as the fact sheet gives it. Do not give the scribe a name.
```

</details>

## Epitaph: Tsetseg the Second

> Here lies Tsetseg the Second, born in 886, died in 951.  
> He made the Grassdiadem of steel and silver.  
> His reign saw strife with Chuluun and Batu.  
> The Keshband fell into quarrel without an heir.

**Validator:** passed after 1 attempt(s).
- last attempt: 434 prompt tokens at 16.2/s, 61 generated at 3.4/s; whole text 45 s

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: figure
name: Tsetseg the Second
role: ruler
people: Keshband
home: Silberscar
born: 886
died: 951
house: House Torsteppe
parent: Tsetseg
grave:
  name: the barrow of Silberscar
  year: 951
artifactsMade:
  - Grassdiadem
ties:
  - kind: rival
    with: Chuluun
    event:
      - 901: Tsetseg the Second and Chuluun become sworn enemies after the battle near Bortedag
  - kind: rival
    with: Batu
    event:
      - 906: Tsetseg the Second and Batu become sworn enemies after the battle near Bortedag
events:
  - 886: Tsetseg the Second of House Torsteppe, child of Tsetseg rises as a ruler of the Keshband at Silberscar
  - 886: Tsetseg the Second makes Grassdiadem of steel and silver at Silberscar
  - 901: Tsetseg the Second and Chuluun become sworn enemies after the battle near Bortedag
  - 906: Tsetseg the Second and Batu become sworn enemies after the battle near Bortedag
  - 951: Tsetseg the Second dies of age
  - 951: Tsetseg the Second dies without an heir, and the Keshband quarrel over the throne
  - 951: Muunokhoi the Reluctant takes the throne of the Keshband and founds House Ulangol

TASK
Write the epitaph carved on the grave of Tsetseg the Second. 2 to 5 short lines, 20 to 50 words in total. Name the dead, the years of birth and death, and one deed or tie from the fact sheet. Solemn tone.
```

</details>

## Epitaph: Gerel

> Here lies Gerel of House Orkhodag, born in 686, died in 751.  
> He forged the Cleaver of Temerwells in 706.  
> His name is remembered in Silberscar.

**Validator:** passed after 1 attempt(s).
- last attempt: 266 prompt tokens at 15.3/s, 53 generated at 3.5/s; whole text 32 s

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: figure
name: Gerel
role: smith
people: Saryhorde
home: Silberscar
born: 686
died: 751
house: House Orkhodag
parent: Khulan
grave:
  name: the barrow of Silberscar
  year: 751
artifactsMade:
  - the Cleaver of Temerwells
events:
  - 686: Gerel of House Orkhodag, child of Khulan rises as a smith of the Saryhorde at Silberscar
  - 706: Gerel makes the Cleaver of Temerwells of wrought iron at Silberscar
  - 736: Winddiadem passes to Gerel
  - 751: Gerel dies of age
  - 751: the Cleaver of Temerwells is laid in the tomb of Gerel

TASK
Write the epitaph carved on the grave of Gerel. 2 to 5 short lines, 20 to 50 words in total. Name the dead, the years of birth and death, and one deed or tie from the fact sheet. Solemn tone.
```

</details>

## Epitaph: Gerel the Unbeaten

> Gerel the Unbeaten  
> Born 541, died 606  
> Slain Skarn, the Mire Mother  
> Near Penpike, keeping the Casket of Ulantau

**Validator:** passed after 2 attempt(s).
- attempt 1: name "Died"
- last attempt: 32 prompt tokens at 15.8/s, 42 generated at 3.4/s; whole text 42 s

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: figure
name: Gerel the Unbeaten
role: general
people: Saryhorde
home: Grimhold
born: 541
died: 606
parent: Temujin the Exacting
grave:
  name: the barrow of Grimhold
  year: 606
artifactsMade:
  - the Casket of Ulantau
events:
  - 541: Gerel the Unbeaten, child of Temujin the Exacting rises as a general of the Saryhorde at Grimhold
  - 596: Gerel the Unbeaten slays Skarn, the Mire Mother near Penpike and keeps the Casket of Ulantau
  - 606: Gerel the Unbeaten dies of age
  - 606: the Casket of Ulantau is laid in the tomb of Gerel the Unbeaten

TASK
Write the epitaph carved on the grave of Gerel the Unbeaten. 2 to 5 short lines, 20 to 50 words in total. Name the dead, the years of birth and death, and one deed or tie from the fact sheet. Solemn tone.
```

</details>

## Epitaph: Gerel the Burned

> Gerel the Burned  
> Born 851, died 916  
> He raised the sanctuary of Karatau.

**Validator:** passed after 1 attempt(s).
- last attempt: 223 prompt tokens at 15.0/s, 28 generated at 3.7/s; whole text 22 s

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: figure
name: Gerel the Burned
role: prophet
people: Saryhorde
home: Karaobo
born: 851
died: 916
grave:
  name: the barrow of Karaobo
  year: 916
events:
  - 851: Gerel the Burned rises as a prophet of the Saryhorde at Karatau
  - 886: Gerel the Burned founds the Keepers of the Fallen Star at Karatau and raises the sanctuary of Karatau
  - 901: Winddiadem passes to Gerel the Burned
  - 901: Farsignet passes to Gerel the Burned

TASK
Write the epitaph carved on the grave of Gerel the Burned. 2 to 5 short lines, 20 to 50 words in total. Name the dead, the years of birth and death, and one deed or tie from the fact sheet. Solemn tone.
```

</details>

## Epitaph: Temujin

> Temujin of the Saryhorde.  
> Born 856, died 921.  
> He received the Farsignet in 916.

**Validator:** passed after 1 attempt(s).
- last attempt: 200 prompt tokens at 14.9/s, 37 generated at 3.6/s; whole text 23 s

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: figure
name: Temujin
role: scholar
people: Saryhorde
home: Karaobo
born: 856
died: 921
grave:
  name: the barrow of Karaobo
  year: 921
events:
  - 856: Temujin rises as a scholar of the Saryhorde at Karatau
  - 916: Farsignet passes to Temujin
  - 921: Temujin dies of age
  - 921: Farsignet is laid in the tomb of Temujin

TASK
Write the epitaph carved on the grave of Temujin. 2 to 5 short lines, 20 to 50 words in total. Name the dead, the years of birth and death, and one deed or tie from the fact sheet. Solemn tone.
```

</details>

## Artifact legend: Winddiadem

> Long ago, in the year 716, Bayan the Patient forged the Winddiadem of wrought iron in Silberscar. It passed through many hands—Gerel, Tsetseg, Yesui the Second, Chuluun, and Gerel the Burned. In 916, it was given to the temple of Bayan the Unseen Weaver at Karatau. But in 926, the Bortekhai took it from Karaobo to Grafell, where it now rests in the temple of Grafell. The crown whispers of winds and old wars, a tale of iron and fate.

**Validator:** passed after 1 attempt(s).
- last attempt: 283 prompt tokens at 14.7/s, 130 generated at 3.3/s; whole text 58 s

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: artifact
name: Winddiadem
form: crown
material: wrought iron
forged: 716
smith: Bayan the Patient
forgedAt: Silberscar
now: kept in the temple of Grafell
homeTemple: Karaobo
events:
  - 716: Bayan the Patient makes Winddiadem of wrought iron at Silberscar
  - 736: Winddiadem passes to Gerel
  - 751: Winddiadem passes to Tsetseg
  - 776: Winddiadem passes to Yesui the Second
  - 841: Winddiadem passes to Chuluun
  - 901: Winddiadem passes to Gerel the Burned
  - 916: Winddiadem is given to the temple of Bayan the Unseen Weaver at Karatau
  - 926: the Bortekhai carry Winddiadem off from Karaobo to Grafell

TASK
Write the legend of Winddiadem as an old storyteller tells it at the fire. One paragraph, 80 to 130 words. Follow the events in order: who made it, of what, where, what became of it, and where it is now.
```

</details>

## Artifact legend: Farsignet

> Long ago, in the year 776, the smith Yesui the Second forged the Farsignet from wrought iron in the town of Bortedag. The ring passed through many hands—first to Chuluun, then to Gerel the Burned, and finally to Temujin. In 921, it was laid in the tomb of Temujin. Now, the Farsignet rests in the barrow of Karaobo, waiting for the day it shall be found again.

**Validator:** passed after 1 attempt(s).
- last attempt: 210 prompt tokens at 15.4/s, 104 generated at 3.4/s; whole text 44 s

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: artifact
name: Farsignet
form: ring
material: wrought iron
forged: 776
smith: Yesui the Second
forgedAt: Bortedag
now: rests in the barrow of Karaobo
events:
  - 776: Yesui the Second makes Farsignet of wrought iron at Bortedag
  - 841: Farsignet passes to Chuluun
  - 901: Farsignet passes to Gerel the Burned
  - 916: Farsignet passes to Temujin
  - 921: Farsignet is laid in the tomb of Temujin

TASK
Write the legend of Farsignet as an old storyteller tells it at the fire. One paragraph, 80 to 130 words. Follow the events in order: who made it, of what, where, what became of it, and where it is now.
```

</details>

## Artifact legend: Hollowcodex

> In the year 446, the smith Kunimund of Highstein crafted the Hollowcodex from vellum, a tome of great weight and mystery. It passed to Kunimund the Reluctant in 506, then to Trevanion the Unquiet in 571. By 636, it was laid in the tomb of Trevanion the Unquiet. Now, it rests in the barrow of Grafell, waiting for the day when its secrets may be read once more.

**Validator:** passed after 1 attempt(s).
- last attempt: 193 prompt tokens at 15.4/s, 110 generated at 3.5/s; whole text 44 s

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: artifact
name: Hollowcodex
form: tome
material: vellum
forged: 446
smith: Kunimund
now: rests in the barrow of Grafell
events:
  - 446: Kunimund makes Hollowcodex of vellum at Highstein
  - 506: Hollowcodex passes to Kunimund the Reluctant
  - 571: Hollowcodex passes to Trevanion the Unquiet
  - 636: Hollowcodex is laid in the tomb of Trevanion the Unquiet

TASK
Write the legend of Hollowcodex as an old storyteller tells it at the fire. One paragraph, 80 to 130 words. Follow the events in order: who made it, of what, where, what became of it, and where it is now.
```

</details>

## Hymn: Bayan the Unseen Weaver

> O Trevanion the Unseen Weaver,  
> weave our fates in threads of light.  
> In Erzvein’s halls we sing thy name,  
> and in Grimhold’s dark we find thy flame.  
> Thy sacred beast, the insect’s flight,  
> guides our steps through shadowed night.  
> Hear our prayers, O unseen lord,  
> and shape our paths as thou art sworn.

**Validator:** passed after 1 attempt(s).
- last attempt: 390 prompt tokens at 15.1/s, 87 generated at 3.2/s; whole text 53 s

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: deity
name: Bayan the Unseen Weaver
domain: fate
namesByPeople:
  - people: Boshold
    name: Trevanion the Unseen Weaver
  - people: Saryhorde
    name: Bayan the Unseen Weaver
  - people: Keshband
    name: Bayan the Unseen Weaver
  - people: Bortekhai
    name: Bayan the Unseen Weaver
patronOf:
  - Boshold
  - Saryhorde
  - Keshband
  - Bortekhai
templesIn:
  - Erzvein
  - Grimhold
  - Silberscar
  - Trestein
  - Ulangol
  - Karcrag
  - Wolktor
  - Wheatbridge
  - Steingrube
  - Stanhold
  - Steingrube
  - Subeordu
  - Ironscar
  - Grafell
  - Orkhosteppe
  - Erzshaft
  - Grafdelve
  - Karaobo
  - Graftor
  - Bortedag
  - Penpike
  - Longleigh
  - Steindelve
  - Deepike
  - Steintal
  - Highdelve
  - Steingrube
  - Bortebulak
orderLeaning: the Order of the Circle
sacredBeast: insect

TASK
Write a hymn to Trevanion the Unseen Weaver, the god of fate, as the Boshold sing it. 6 to 10 short lines. Speak to the god by the name the Boshold use. Draw on the god's domain and on anything else the fact sheet says about temples, cults or events.
```

</details>

## Hymn: Erdene of the Black Earth

> O Erwin of the Black Earth,  
> listener of the silent grave,  
> we sing to you in dust and bone,  
> as the dead rise in your name.  
> The land weeps where Steingrube fell,  
> and we call you, god of death,  
> to walk the dark and hold the end.  
> We are yours, in shadow and in sleep.

**Validator:** passed after 1 attempt(s).
- last attempt: 251 prompt tokens at 15.1/s, 78 generated at 3.5/s; whole text 39 s

<details><summary>Fact sheet, as the model saw it</summary>

```text
FACT SHEET
kind: deity
name: Erdene of the Black Earth
domain: death
namesByPeople:
  - people: Boshold
    name: Erwin of the Black Earth
  - people: Saryhorde
    name: Erdene of the Black Earth
  - people: Keshband
    name: Erdene of the Black Earth
  - people: Bortekhai
    name: Erdene of the Black Earth
worshippedSince: 211
orderLeaning: the Order of the Circle
sacredBeast: undead
events:
  - 211: after Steingrube is given up to the blight and stands empty, people turn to Erwin of the Black Earth
  - 211: Steingrube is given up to the blight and stands empty

TASK
Write a hymn to Erwin of the Black Earth, the god of death, as the Boshold sing it. 6 to 10 short lines. Speak to the god by the name the Boshold use. Draw on the god's domain and on anything else the fact sheet says about temples, cults or events.
```

</details>
