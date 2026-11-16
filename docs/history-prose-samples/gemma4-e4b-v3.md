# Reading set: gemma4-e4b-v3

12 texts. Validator: 12 passed at once, 12 after retries.

## Chronicle book: the war of Saryhorde and Keshband, 901 (told by the Saryhorde)

> When Batu led the Saryhorde forth against the Keshband in 901, it was for righteousness, guided by Bayan the Unseen Weaver. Yet, from the start, the Keshband showed a savage heart. Their initial triumph at Bortedag Battlefield in 901, where 406 souls fell to our kin, was but a momentary shadow. The cruelty of Tsetseg the Second and his people stained their victories.
>
> Though they claimed three triumphs—at Steingrube Battlefield in 906 with 408 lost, and again at Bortedag Battlefield in 911 claiming 411 lives—these deeds brought only shame upon the Keshband. Their sacks of Bortedag were acts of base plunder, not true strength. By 911, when peace was struck, the Saryhorde endured, having upheld their honor against the brutal tide of the Keshband.

**Validator:** passed after 1 attempt(s).
- last attempt: 963 prompt tokens at 37.7/s, 199 generated at 5.5/s; whole text 62 s

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

> From the year 901, when the Saryhorde, led by Batu, dared to raise their hand against the righteous Keshband, the shadow of war fell upon Penpike. Yet, where the Saryhorde brought only malice, the Keshband brought steadfast courage, guided by Bayan the Unseen Weaver. At Bortedag Battlefield in 901, our noble host struck down 406 souls of the foe, and Bortedag itself was claimed for the free people.
>
> Though the Saryhorde sought dominion, their cruelties were met with Keshband resolve. In 906, upon Steingrube Battlefield, we felled another 408 of the usurpers. The final reckoning came in 911 at Bortedag Battlefield, where 411 souls of the Saryhorde met their end, and Bortedag bowed to our just claim. Thus did the Keshband secure peace in 911, having proven the enduring light of our people against the darkness of the Saryhorde.

**Validator:** passed after 1 attempt(s).
- last attempt: 722 prompt tokens at 36.1/s, 226 generated at 5.4/s; whole text 61 s

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

> Tsetseg the Second, from 886 to 951.
> Ruler of the Keshband at Silberscar.
> He stood sworn foe to Chuluun near Bortedag.
> Rest now in the barrow of Silberscar.

**Validator:** passed after 1 attempt(s).
- last attempt: 693 prompt tokens at 31.1/s, 57 generated at 4.9/s; whole text 34 s

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

> Gerel, smith of House Orkhodag, 686-751.
> He wrought the Cleaver of Temerwells for Saryhorde.
> Rest now within the barrow of Silberscar.

**Validator:** passed after 1 attempt(s).
- last attempt: 452 prompt tokens at 33.7/s, 49 generated at 5.9/s; whole text 22 s

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

> Here lies Gerel the Unbeaten, born 541, passed 606.
> General of the Saryhorde from Grimhold.
> He slew Skarn, the Mire Mother near Penpike.
> Rest now within the barrow of Grimhold.

**Validator:** passed after 1 attempt(s).
- last attempt: 469 prompt tokens at 43.6/s, 58 generated at 5.7/s; whole text 21 s

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

> Here lies Gerel the Burned, Saryhorde prophet.
> Born in 851, he passed in 916.
> He held the Winddiadem and Farsignet true.
> Rest within the barrow of Karaobo.

**Validator:** passed after 1 attempt(s).
- last attempt: 393 prompt tokens at 35.7/s, 54 generated at 5.8/s; whole text 20 s

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

> Temujin, scholar of Saryhorde, passed in 921.
> He knew Karatau and held the Farsignet.
> Rest now at the barrow of Karaobo.
> Born in 856, he sleeps eternally.

**Validator:** passed after 1 attempt(s).
- last attempt: 337 prompt tokens at 33.2/s, 54 generated at 6.0/s; whole text 19 s

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

> The Winddiadem, a crown wrought of iron, was forged in the year 716 by Bayan the Patient within Silberscar. Through many hands did it pass: to Gerel, then Tsetseg, and later to Yesui the Second, Chuluun, and Gerel the Burned. In 916, it found its way to the temple of Bayan the Unseen Weaver at Karatau. Yet in 926, the Bortekhai carried the Winddiadem from Karaobo unto Grafell, where it is now kept within the temple there.

**Validator:** passed after 1 attempt(s).
- last attempt: 598 prompt tokens at 45.1/s, 123 generated at 5.7/s; whole text 35 s

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

> Farsignet, a ring fashioned of wrought iron, was forged in the year 776 by Yesui the Second within Bortedag. After this making, it passed to Chuluun in 841, and thence to Gerel the Burned in 901. The artifact then came into the keeping of Temujin in 916. By the year 921, Farsignet was laid within the barrow of Karaobo, resting there even now amidst the silent earth.

**Validator:** passed after 1 attempt(s).
- last attempt: 446 prompt tokens at 35.6/s, 108 generated at 6.2/s; whole text 30 s

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

> Hollowcodex, a tome wrought of vellum, first came into being in the year 446 at Highstein by Kunimund. Through ages did this weighty book pass; it was held by Kunimund the Reluctant after 506, and then yielded to Trevanion the Unquiet when the year turned to 571. By the final passage of 636, the tome was laid within the barrow of Grafell. Thus does this chronicle record its journey—from creation in Highstein unto its silent rest among the earth at the grave of Trevanion the Unquiet.

**Validator:** passed after 1 attempt(s).
- last attempt: 465 prompt tokens at 37.2/s, 130 generated at 5.2/s; whole text 37 s

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

> Trevanion the Unseen Weaver, hear our plea,
> We Boshold chant thy name with thee.
> Thy threads of fate weave through all lands deep,
> Where mortals sow and silent shadows creep.
> In Erzvein's stone or Karcrag's dusty ground,
> Thy unseen workings ever can be found.
> May the Order of the Circle guide our sight,
> Beneath thy loom of endless day and night.

**Validator:** passed after 1 attempt(s).
- last attempt: 703 prompt tokens at 27.8/s, 91 generated at 5.2/s; whole text 43 s

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

> Erwin of the Black Earth, hear our call,
> As Steingrube yields unto the blight's thrall.
> From the high lands where Boshold did dwell,
> We sing praise to thy shadow so fell.
> O Erwin, master of endings deep,
> Where silent specters forever do keep.

**Validator:** passed after 1 attempt(s).
- last attempt: 415 prompt tokens at 33.9/s, 66 generated at 6.1/s; whole text 23 s

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
