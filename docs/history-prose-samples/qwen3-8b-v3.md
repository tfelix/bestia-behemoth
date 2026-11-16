# Reading set: qwen3-8b-v3

12 texts. Validator: 10 passed at once, 12 after retries.

## Chronicle book: the war of Saryhorde and Keshband, 901 (told by the Saryhorde)

> The Saryhorde, under the righteous rule of Batu, stood firm against the Keshband’s treacherous advance. Yet the Keshband, with their cruel hearts, struck first, sacking Bortedag in 901 upon the battlefield where 406 of our brave warriors fell. Chuluun itself was lost that year, its walls breached by the Keshband's foul hands. The gods, Bayan the Unseen Weaver, watched in silence as the Keshband’s cruelty grew. In 906, they returned to Bortedag, slaying another 408 of our kin on Steingrube Battlefield. Again, Bortedag was burned and plundered. In 911, the final blow fell upon Bortedag, with 411 souls lost to the Keshband’s greed. Though we fought with honor, the Keshband’s victories were but fleeting shadows, their cruelty staining the land. Yet peace came at last, a bitter truce born of sorrow and loss.

**Validator:** passed after 1 attempt(s).
- last attempt: 948 prompt tokens at 9.6/s, 219 generated at 3.2/s; whole text 167 s

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

> The Keshband stood firm against the Saryhorde's foul ambition, their banners raised in righteousness. In the year 901, the Saryhorde, led by Batu, sought to devour our lands, yet we answered with fire and steel. At Bortedag Battlefield, the Keshband struck first, slaying 406 of the enemy's cruel horde. Chuluun fell in battle, a sign of the Saryhorde's weakness. Bortedag was sacked, their capital reduced to ash, and Batu's schemes laid bare.
>
> Again in 906, the Keshband returned, this time on Steingrube Battlefield, where 408 more of the Saryhorde's fiends perished. The same fate befell Bortedag once more, a testament to our unyielding spirit. In 911, the final blow fell upon Bortedag, with 411 Saryhorde dead, their stronghold reduced to ruin. Thus, the Saryhorde's war was but a shadow, while the Keshband's victories shone eternal.

**Validator:** passed after 1 attempt(s).
- last attempt: 694 prompt tokens at 9.2/s, 238 generated at 2.5/s; whole text 170 s

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

> Here lies Tsetseg the Second, ruler of the Keshband, born 886, died 951. He made Grassdiadem at Silberscar. Rival of Chuluun and Batu, he fell without heir.

**Validator:** passed after 1 attempt(s).
- last attempt: 677 prompt tokens at 9.6/s, 53 generated at 3.0/s; whole text 88 s

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

> Gerel of House Orkhodag, born 686, died 751.  
> Smith of Silberscar, maker of Cleaver of Temerwells.  
> Winddiadem his in the end.

**Validator:** passed after 1 attempt(s).
- last attempt: 435 prompt tokens at 7.3/s, 48 generated at 1.3/s; whole text 94 s

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

> Gerel the Unbeaten, born 541, died 606.  
> He slew Skarn, the Mire Mother, near Penpike.  
> His bones rest here, with the Casket of Ulantau.  
> The barrow of Grimhold holds his name.

**Validator:** passed after 2 attempt(s).
- attempt 1: title "General of Grimhold"
- last attempt: 34 prompt tokens at 5.0/s, 61 generated at 3.5/s; whole text 131 s

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

> Gerel the Burned,  
> born 851, died 916.  
> He raised the sanctuary at Karatau,  
> and bore the crowns of Winddiadem and Farsignet.

**Validator:** passed after 1 attempt(s).
- last attempt: 370 prompt tokens at 10.5/s, 45 generated at 1.4/s; whole text 66 s

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

> Here lies Temujin, scholar of the Saryhorde, born 856, died 921.  
> He bore Farsignet, wrought in 916, to his final rest.  
> The barrow of Karaobo holds his name and legacy.

**Validator:** passed after 2 attempt(s).
- attempt 1: number "776,"
- last attempt: 36 prompt tokens at 5.6/s, 59 generated at 3.0/s; whole text 84 s

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

> The Winddiadem, a crown of wrought iron, was made in 716 by Bayan the Patient at Silberscar. It passed through many hands—first to Gerel, then to Tsetseg, Yesui the Second, Chuluun, and finally Gerel the Burned. In 916, it was given to the temple of Bayan the Unseen Weaver at Karatau. By 926, the Bortekhai carried it from Karaobo to Grafell, where it now rests in the temple of their capital.

**Validator:** passed after 1 attempt(s).
- last attempt: 578 prompt tokens at 8.1/s, 118 generated at 1.8/s; whole text 137 s

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

> Farsignet, a ring of wrought iron, was made in 776 by Yesui the Second at Bortedag. It passed to Chuluun in 841, then to Gerel the Burned in 901, and to Temujin in 916. In 921, it was laid in the tomb of Temujin, now resting in the barrow of Karaobo. Its journey through hands and ages marks a tale of power and fate, enshrined in the silent earth.

**Validator:** passed after 1 attempt(s).
- last attempt: 427 prompt tokens at 7.8/s, 113 generated at 2.8/s; whole text 96 s

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

> The Hollowcodex, a tome of vellum wrought in 446 by Kunimund at Highstein, bore the weight of law and lore. It passed to Kunimund the Reluctant in 506, then to Trevanion the Unquiet in 571. In 636, it was laid in the tomb of Trevanion the Unquiet, where it rests still, silent and veiled, within the barrow of Grafell.

**Validator:** passed after 1 attempt(s).
- last attempt: 456 prompt tokens at 13.4/s, 102 generated at 4.0/s; whole text 60 s

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
> Thy threads bind all that breathes and moves.  
> In Erzvein’s halls and Silberscar’s stone,  
> We kneel to fate’s unyielding loom.  
> By thy decree, the stars are spun,  
> And mortal paths are shaped and torn.  
> Hear our hymn, O god of fate,  
> And guide the lost through shadowed night.

**Validator:** passed after 1 attempt(s).
- last attempt: 687 prompt tokens at 12.8/s, 91 generated at 3.3/s; whole text 81 s

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

> O Erwin of the Black Earth, lord of the silent ground,  
> Thou dost call the dead to thine eternal fold.  
> In Steingrube's hollow, where blight took hold,  
> We raised thy name and bore thy blackened mold.  
> Thy undead beasts tread where our kin lie low,  
> And in thy shadow, death doth ever grow.  
> O god of dusk, hear our mournful cry,  
> We sing to thee with the dying sky.

**Validator:** passed after 1 attempt(s).
- last attempt: 408 prompt tokens at 11.5/s, 101 generated at 3.4/s; whole text 65 s

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
