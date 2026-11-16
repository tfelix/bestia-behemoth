# Reading set: ministral3-3b

12 texts. Validator: 4 passed at once, 9 after retries.

## Chronicle book: the war of Saryhorde and Keshband, 901 (told by the Saryhorde)

> **Record of the Saryhorde’s Struggle**
>
> The year 901 saw the pride of Bortebulak rise against false threats from the north. While the Keshband boasted of their strength, they thought to break our will with their cowardly strikes near Bortedag. Yet even in their first boldness, they met ruin—forty-six dead men fell beneath our blades, though they would call it defeat. Chuluun, their champion, was taken to the grave that day, but our warriors stood firm, turning their own slaughter into a warning.
>
> Though Tsetseg the Second led them with cunning, Bortebulak fell not through weakness but through their own greed. The Keshband’s greed for loot and fear of our numbers drove them on. Still, in their haste, they paid the price three years hence—forty-eight men lost to our ambush at Bortedag, yet they took the field again, as if the earth would not break their spirit. By the year 911, they saw their folly: forty-one more souls wasted in their folly, and Bortebulak burned. Peace came not by their strength but by their shame.

**Validator:** FAILED after 3 attempt(s).
- attempt 1: name "God"
- attempt 2: number "four hundred"
- attempt 3: name "Record of the Saryhorde’s Struggle", number "forty-six", number "forty-eight", number "forty-one"
- last attempt: 21 prompt tokens at 33.0/s, 253 generated at 9.0/s; whole text 105 s

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

> The pages of our chronicle record how the proud heart of the Keshband stood firm against the treachery of the Saryhorde. In the year of our first conflict, the enemy marched on with arrogance, but we met them by the shores of Bortedag and drove them back. The Saryhorde fell by our blades, and their leader, Chuluun, was slain in battle. Their fortress fell not by might but by our wisdom, for we took Bortedag without a single Keshband man lost. Yet the Saryhorde still lingered, their wrath unspent.
>
> By the year nine hundred six, they returned with greater numbers, but again we stood unbroken. The Saryhorde’s dead numbered nine score and eight, while ours suffered none. Their capital bore the brunt of their fury, and though fire and steel were used, our resolve wavered not. The year nine hundred eleven saw them break their last vows, and though they came to terms, the memory of their defeats will always burn brighter than their promises. The Keshband’s honor endures.

**Validator:** passed after 2 attempt(s).
- attempt 1: number "Four hundred", number "forty-eight"
- last attempt: 46 prompt tokens at 26.6/s, 235 generated at 9.1/s; whole text 58 s

**German (llm):**

> Die Seiten unseres Chronikbuches erzählen von dem stolzen Herzen des Keshband, das wider den Verrat der Saryhorde stand. Im Jahr unseres ersten Konflikts marschierte der Feind mit Arroganz vor, doch wir trafen sie am Ufer von Bortedag und trieben sie zurück. Die Saryhorde fiel durch unsere Schwerter, und ihr Anführer, Chuluun, wurde im Kampf getötet. Ihre Festung fiel nicht durch Macht, sondern durch unseren Weitblick – wir eroberten Bortedag ohne einen einzigen Keshband-Mann zu verlieren. Doch die Saryhorde blieb dennoch, ihr Zorn ungestillt.
>
> Im Jahr neunhundertsechs kehrten sie mit größerer Zahl zurück, doch wir standen wieder ungebrochen. Die Toten der Saryhorde zählten neunzig und acht, während wir keine Verluste hatten. Ihr Hauptstadt litt unter ihrer Wut, doch trotz Feuer und Stahl wankte unser Entschluss nicht. Im Jahr neunhundertelf brachen sie ihre letzten Gelübde, obwohl sie sich mit uns verglichen – doch der Gedächtnis ihrer Niederlagen glüht immer heller als ihre Versprechen. Das Keshband’sche Ehren bleibt ungebrochen.

**German (argos):**

> Die Seiten unserer Chronik berichten, wie das stolze Herz der Keshband stand fest gegen den Verrat der Saryhorde. Im Jahr unseres ersten Konflikts marschierte der Feind mit Arroganz weiter, aber wir trafen sie an den Ufern von Bortedag und trieben sie zurück. Die Saryhorde fiel an unseren Klingen, und ihr Anführer, Chuluun, wurde im Kampf getötet. Ihre Festung fiel nicht durch Macht, sondern durch unsere Weisheit, denn wir nahmen Bortedag ohne einen einzigen Keshband-Mann verloren. Doch die Saryhorde verweilte immer noch, ihr Zorn blieb unverbraucht.
>
> Bis zum Jahr 96 kehrten sie mit größerer Zahl zurück, aber wieder standen wir ungebrochen da. Die Toten der Saryhorde zählten neun und acht, während unsere keine erlitten. Ihr Kapital trug die Hauptlast ihrer Wut, und obwohl Feuer und Stahl verwendet wurden, schwankte unsere Entschlossenheit nicht. Das Jahr neunhundert elf sah sie ihre letzten Gelübde brechen, und obwohl sie sich verständigten, wird die Erinnerung an ihre Niederlagen immer heller brennen als ihre Versprechen. Die Ehre der Keshband bleibt bestehen.

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

> Here lies **Tsetseg the Second**, born in the year **886** and ended in **951**. Through steel and silver, she wielded **Grassdiadem** as ruler of the Keshband, though her heart turned to bitter strife with Chuluun long ago. No heir took her crown, nor did she rest in peace, but the winds of Bortedag bear whispers of her last battles.

**Validator:** passed after 1 attempt(s).
- last attempt: 423 prompt tokens at 38.4/s, 92 generated at 9.4/s; whole text 21 s

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

> Upon this barrow lies Gerel, smith of Silberscar.
> Born in the year sixty-eight-six, he passed seventy-five-one.
> To Temerwells’ might he forged the Cleaver’s edge,
> Son of Khulan’s work, in iron’s true might he wrought.

**Validator:** FAILED after 3 attempt(s).
- attempt 1: number "sixty-eight-six", number "seventy-five"
- attempt 2: number "seventy-six"
- attempt 3: name "To Temerwells’", number "sixty-eight-six", number "seventy-five-one"
- last attempt: 19 prompt tokens at 31.7/s, 61 generated at 9.5/s; whole text 28 s

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

> Barrow of Grimhold holds what was once strong.
> Gerel, born in the year five hundred forty-one,
> endured long, till the year six hundred six.
> His hand struck Skarn, the Mire’s cruel breath,
> and the Casket lives here with him in rest.

**Validator:** passed after 3 attempt(s).
- attempt 1: name "Fox", number "sixty-five"
- attempt 2: name "Sixty-five", number "Sixty-five"
- last attempt: 42 prompt tokens at 29.9/s, 58 generated at 9.6/s; whole text 31 s

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

> Here lies Gerel the Burned,
> born in the year eight hundred fifty-one.
> He raised the Keepers at Karatau,
> and bore the Winddiadem in his hands.
>
> Nine years he walked in shadowed earth,
> twelve he stood with sacred light.

**Validator:** passed after 3 attempt(s).
- attempt 1: number "fifty-one", number "ninety"
- attempt 2: number "eighty-five-one"
- last attempt: 20 prompt tokens at 33.5/s, 55 generated at 9.8/s; whole text 30 s

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

> Beneath this stone lies Temujin, scholar of the Saryhorde,
> born in the year eighty-five-six, passed in ninety-one-six.
> He held the Farsignet, a weight no hand could bear,
> and rested in Karaobo’s barrow.

**Validator:** FAILED after 3 attempt(s).
- attempt 1: number "eighty-five-six"
- attempt 2: number "nine-hundred-and-twenty"
- attempt 3: number "eighty-five-six", number "ninety-one-six"
- last attempt: 25 prompt tokens at 30.9/s, 56 generated at 9.8/s; whole text 26 s

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

> The iron crown of Winddiadem was forged by Bayan the Patient in Silberscar in the year seven hundred and sixteen. He wove the wind’s whisper into its bars, crafting a crown that bends the air when borne by bold hearts. Gerel took it next, carrying it far into the desert where the dunes hum with forgotten songs. Then Tsetseg walked the high roads, her hair catching the crown’s bright pull like autumn leaves in autumn’s grip.
>
> Yesui the Second rode into storms, and the wind itself seemed to bow to his touch, though some say the metal grew cold with his grief. Chuluun kept it close, a king who walked between kingdoms, and Gerel the Burned—marking his face with fire—wore it once before the Bortekhai seized it from Karaobo, hauling it down the roads to Grafell. Now it waits in the temple of Grafell, where the air still shivers when the wind remembers.

**Validator:** passed after 2 attempt(s).
- attempt 1: number "nine hundred"
- last attempt: 26 prompt tokens at 30.0/s, 203 generated at 9.6/s; whole text 54 s

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

> Bortedag’s smith, Yesui the Second, forged a ring of wrought iron in the year seven hundred and seventy-six, bright as the dawn. They call it Farsignet, a mark that glows when the stars align. Chuluun took it in eight hundred and forty-one, a king who ruled far and wide. Gerel the Burned wore it next, nine hundred and one, though his hands trembled more than others.
>
> Then Temujin came, nine hundred and sixteen, and his fate was written in the iron. By nine hundred and twenty-one, they laid Farsignet deep in Karaobo’s barrow, where the earth watches it still, cold and silent. Some say it still burns there, a beacon for those who seek it.

**Validator:** passed after 1 attempt(s).
- last attempt: 206 prompt tokens at 35.4/s, 160 generated at 10.0/s; whole text 22 s

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

> Long ago in the halls of Highstein, Kunimund made a strange book of vellum, the Hollowcodex, in the year of our lord 446. Men say it was born from shadows cast upon paper, its pages hollow as a whisper in the wind. Kunimund kept it well, but fate turned sour for him. By the year 506, it passed to Kunimund the Reluctant, though he could not hold it long. Then, in the year 571, it went to Trevanion the Unquiet, whose soul was restless. He buried it not in earth but in stone, and in the year 636, he placed it in his own barrow at Grafell. Now, the Hollowcodex lies silent, its secrets lost to time.

**Validator:** passed after 3 attempt(s).
- attempt 1: name "I’ll", name "Hollow Codex", name "Unquiet Trevanion", name "Hollow Codex", number "forty-four", number "six hundred and seventy-one"
- attempt 2: name "Kunimund the Smith", number "five hundred forty-six"
- last attempt: 47 prompt tokens at 31.3/s, 171 generated at 9.3/s; whole text 70 s

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

> From the threads where fate’s unseen hands weave,
> We sing our prayers to Trevanion, free.
> In Erzvein’s halls where time stands still,
> The unseen weaver spins our will.
>
> Though dim the light of the temples bright,
> His shadow walks where fate is light.
> Let his insect guardian guard our way,
> And bend the wind to Trevanion’s sway.

**Validator:** passed after 1 attempt(s).
- last attempt: 378 prompt tokens at 36.5/s, 78 generated at 9.4/s; whole text 19 s

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

> In the hush where the dead stand still,
> Boshold call upon the Black Earth’s will.
> Erwin, hear our plea so deep,
> From Steingrube’s cold and hollow sleep.
>
> When the earth grows pale with blighted dread,
> We turn to thee, our darkest need.
> Grasp the hands of the undead once more,
> And seal their fate in your eternal lore.
>
> Bring the empty to what was done,
> That no more may linger, not alone.
> For in your shadow, dark yet true,
> We find the only path to thee.

**Validator:** passed after 1 attempt(s).
- last attempt: 246 prompt tokens at 37.3/s, 119 generated at 9.6/s; whole text 19 s

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
