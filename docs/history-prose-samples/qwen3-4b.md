# Reading set: qwen3-4b

12 texts. Validator: 5 passed at once, 8 after retries.

## Chronicle book: the war of Saryhorde and Keshband, 901 (told by the Saryhorde)

> the war of Saryhorde and Keshband began in 901. The Saryhorde went to war with the Keshband as the Keshband first struck. The Keshband defeated the Saryhorde near Bortedag, 406 dead. Chuluun fell in battle. Bortedag was sacked by the Keshband. The Saryhorde was not without courage, but the Keshband was swift and strong. In 906, the Keshband again defeated the Saryhorde near Bortedag, 408 dead. Bortedag was sacked again. The Keshband was ever victorious, but the Saryhorde was ever ready. In 911, the Keshband defeated the Saryhorde once more, 411 dead. Bortedag was sacked a third time. Yet the Saryhorde and Keshband came to terms. The Keshband was strong, the Saryhorde was not. The Keshband was favored by Bayan the Unseen Weaver. The Saryhorde was not.

**Validator:** FAILED after 3 attempt(s).
- attempt 1: case "chuluun", lowercase "the"
- attempt 2: lowercase "the"
- attempt 3: lowercase "the"
- last attempt: 24 prompt tokens at 20.5/s, 237 generated at 7.4/s; whole text 130 s

**German (llm):**

> Der Krieg der Saryhorde und Keshband begann im 901. Die Saryhorde ging gegen die Keshband zum Krieg, da die Keshband zuerst schlug. Die Keshband besiegte die Saryhorde nahe Bortedag, 406 Tote. Chuluun fiel im Kampf. Bortedag wurde von den Keshband gestohlen. Die Saryhorde war nicht ohne Mut, doch die Keshband war schnell und stark. Im 906 besiegte die Keshband die Saryhorde erneut nahe Bortedag, 408 Tote. Bortedag wurde wieder gestohlen. Die Keshband war immer siegend, doch die Saryhorde war immer bereit. Im 911 besiegte die Keshband die Saryhorde nochmals, 411 Tote. Bortedag wurde drittens gestohlen. Doch die Saryhorde und Keshband kamen zu Verträgen. Die Keshband war stark, die Saryhorde nicht. Die Keshband war von Bayan dem Unsehnen Webber begünstigt. Die Saryhorde nicht.

**German (argos):**

> Der Krieg von Saryhorde und Keshband begann 901. Die Saryhorde zog mit dem Keshband in den Krieg, als das Keshband zuerst zuschlug. Die Keshband besiegte die Saryhorde in der Nähe von Bortedag, 406 tot. Chuluun fiel im Kampf. Bortedag wurde von der Keshband entlassen. Die Saryhorde war nicht ohne Mut, aber der Keshband war schnell und stark. Im Jahr 906 besiegte die Keshband erneut die Saryhorde in der Nähe von Bortedag, 408 Tote. Bortedag wurde wieder entlassen. Die Keshband war immer siegreich, aber die Saryhorde war immer bereit. Im Jahr 911 besiegte die Keshband die Saryhorde noch einmal, 411 tot. Bortedag wurde ein drittes Mal entlassen. Doch die Saryhorde und Keshband kamen zu einer Einigung. Die Keshband war stark, die Saryhorde nicht. Der Keshband wurde von Bayan dem Unsichtbaren Weber bevorzugt. Die Saryhorde war es nicht.

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

> the war of Saryhorde and Keshband, 901, was a time of great strife and trial. the Keshband, under the patronage of Bayan the Unseen Weaver, stood firm against the Saryhorde. the Saryhorde, led by Batu, sought to extend their dominion, but the Keshband, with Tsetseg the Second at their head, met them with courage. in 901, the Keshband defeated the Saryhorde near Bortedag, 406 dead. the field was bloodstained, and the Saryhorde were forced to retreat. the Saryhorde, ever unyielding, returned in 906, but again the Keshband prevailed, 408 dead. the Saryhorde's fate was sealed, and Bortedag was sacked once more. in 911, the Keshband, with the aid of Bayan, defeated the Saryhorde again, 411 dead. the war ended in 911, and the Keshband, ever steadfast, prevailed. the Saryhorde, though mighty, could not withstand the will of the gods and the strength of the Keshband.

**Validator:** FAILED after 3 attempt(s).
- attempt 1: case "chuluun", lowercase "the"
- attempt 2: lowercase "the"
- attempt 3: lowercase "the"
- last attempt: 24 prompt tokens at 23.1/s, 266 generated at 4.9/s; whole text 139 s

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

> Tsetseg the Second. Born 886. Died 951. Made Grassdiadem of steel and silver. Rival of Chuluun. Died without heir. Keshband quarrel. Muunokhoi takes throne. House Ulangol found.

**Validator:** passed after 1 attempt(s).
- last attempt: 434 prompt tokens at 27.9/s, 59 generated at 4.9/s; whole text 27 s

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

> Gerel of House Orkhodag. Born 686. Died 751. Made the Cleaver of Temerwells.

**Validator:** passed after 1 attempt(s).
- last attempt: 266 prompt tokens at 28.4/s, 32 generated at 5.4/s; whole text 15 s

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
> Born 541  
> Died 606  
> He slayed Skarn, the Mire Mother, and kept the Casket of Ulantau.

**Validator:** passed after 1 attempt(s).
- last attempt: 267 prompt tokens at 25.6/s, 42 generated at 5.4/s; whole text 18 s

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

> Gerel the Burned. Born 851. Died 916. He raised the sanctuary of Karatau.

**Validator:** passed after 1 attempt(s).
- last attempt: 223 prompt tokens at 26.1/s, 28 generated at 4.8/s; whole text 14 s

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

> Temujin. Born 856. Died 921. He bore the weight of the Saryhorde's knowledge. The barrow of Karaobo. Farsignet rests where he fell.

**Validator:** passed after 1 attempt(s).
- last attempt: 200 prompt tokens at 26.2/s, 45 generated at 5.3/s; whole text 16 s

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

> Long ago, in the year 716, Bayan the Patient forged the Winddiadem of wrought iron at Silberscar. It passed to Gerel, then to Tsetseg, then to Yesui the Second, and finally to Chuluun. In 916, it was given to the temple of Bayan the Unseen Weaver at Karatau, but in 926, the Bortekhai carried it from Karaobo to Grafell. Now it is kept in the temple of Grafell, where it rests among the old stones and the wind that sings through the halls.

**Validator:** passed after 3 attempt(s).
- attempt 1: case "bayan", case "bortekhai", case "chuluun", case "gerel", case "grafell", case "karatau", case "silberscar", case "tsetseg", case "winddiadem", case "yesui", lowercase "the"
- attempt 2: case "bayan", case "bortekhai", case "chuluun", case "gerel", case "grafell", case "karatau", case "silberscar", case "tsetseg", case "winddiadem", case "yesui", lowercase "long"
- last attempt: 19 prompt tokens at 33.9/s, 127 generated at 5.0/s; whole text 104 s

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

> Long ago, in the year 776, Yesui the Second forged Farsignet of wrought iron at Bortedag. It was a ring of great power, shaped by the hands of a master smith. Soon after, it passed to Chuluun, who wore it with pride. Then it went to Gerel the Burned, who bore the weight of its legacy. Later, it was given to Temujin, who used it in his great endeavors. At last, in the year 921, Farsignet was laid in the tomb of Temujin, and now it rests in the barrow of Karaobo, where it waits for the day it may be found again.

**Validator:** passed after 3 attempt(s).
- attempt 1: case "bortedag", case "chuluun", case "farsignet", case "gerel", case "karaobo", case "temujin", case "yesui", lowercase "in"
- attempt 2: case "bortedag", case "chuluun", case "farsignet", case "gerel", case "karaobo", case "temujin", case "yesui", lowercase "long"
- last attempt: 19 prompt tokens at 38.3/s, 145 generated at 4.8/s; whole text 95 s

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

> in the year 446, kunimund made hollowcodex of vellum at highstein. it was a tome of great power, written in the dark script of the old tongues. in 506, it passed to kunimund the reluctant, a man who did not wish the weight of its secrets. in 571, it went to trevanion the unquiet, a sorcerer who sought the truth in the shadows. in 636, it was laid in the tomb of trevanion the unquiet, and now it rests in the barrow of grafell, where the wind carries the whispers of the past.

**Validator:** FAILED after 3 attempt(s).
- attempt 1: case "grafell", case "highstein", case "hollowcodex", case "kunimund", case "trevanion", case "unquiet", lowercase "in"
- attempt 2: case "grafell", case "highstein", case "hollowcodex", case "kunimund", case "trevanion", case "unquiet", lowercase "in"
- attempt 3: case "grafell", case "highstein", case "hollowcodex", case "kunimund", case "trevanion", case "unquiet", lowercase "in"
- last attempt: 1 prompt tokens at 5.5/s, 138 generated at 4.9/s; whole text 103 s

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

> Trevanion the Unseen Weaver  
> Bless us, Trevanion, with the thread of fate.  
> We are the Boshold, and we walk the path of change.  
> In Erzvein, we find the signs of your will.  
> In Grimhold, the threads are woven tight.  
> We bow to the Order of the Circle, ever loyal.  
> The insect is our guide, and the sacred beast is ours.  
> In Steingrube, we hear the whispers of the past.  
> We are the keepers of the unseen weave.  
> Bless us, Trevanion, and let the threads be true.

**Validator:** passed after 2 attempt(s).
- attempt 1: case "boshold", case "erzvein", case "grimhold", case "longleigh", case "steingrube", case "trevanion", lowercase "trevanion"
- last attempt: 133 prompt tokens at 31.1/s, 134 generated at 4.4/s; whole text 73 s

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

> erwin of the black earth  
> hear us in the stillness where the dead walk  
> the blight has taken steingrube and left it empty  
> we kneel in the shadow of the circle's keep  
> the undead guard the gates of the forgotten  
> we offer our breath to the earth's cold embrace  
> the dead are numbered and the dead are known  
> we walk the paths where the dead have been  
> the order of the circle watches over the dead  
> we are the breath of the earth and the dust of the dead.

**Validator:** FAILED after 3 attempt(s).
- attempt 1: case "steingrube", lowercase "erwin"
- attempt 2: case "steingrube", lowercase "erwin"
- attempt 3: case "steingrube", lowercase "erwin"
- last attempt: 1 prompt tokens at 3.5/s, 109 generated at 4.2/s; whole text 67 s

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
