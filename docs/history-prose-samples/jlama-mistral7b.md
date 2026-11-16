# Reading set: jlama-mistral7b

12 texts. Validator: 3 passed at once, 7 after retries.

## Chronicle book: the war of Saryhorde and Keshband, 901 (told by the Saryhorde)

> In the Year of the Unseen Weaver's Loom, 901, a dark cloud of war descended upon our fair land of Saryhorde. The vile and treacherous folk of Keshband, led by the cowardly Tsetseg the Second, dared to raise their banners against us.
>
> Upon the verdant fields of Bortedag, our valiant warriors met their foes, only to be betrayed by the treachery of the Keshband. Our brave men fell beneath the cruel blades of their enemies, leaving behind our fallen champions, Chuluun among them, a hero remembered for his bravery.
>
> Yet, though we suffered losses, we did not falter. In the Years that followed, we met the Keshband upon the fields of Steingrube and Bortedag once more, each time losing ground but never our spirits.
>
> In the Year of the Weaver's Triumph, 911, our wise ruler Batu, guided by the watchful eye of Bayan the Unseen Weaver, forged a peace with the Keshband. A bitter pill to swallow, but one that saved our people from further bloodshed.
>
> Thus, the tale of our people's resilience echoes through the ages, a testament to our unwavering spirit and the strength of our land, the land we call Saryhorde. May Bayan the Unseen Weaver continue to watch over us.

**Validator:** FAILED after 3 attempt(s).
- attempt 1: name "The Chronicle of Bortebulak"
- attempt 2: name "The Chronicle of the War of Saryhorde", name "The Battle of Bortedag"
- attempt 3: name "In the Year of the Unseen Weaver's Loom", name "In the Years", name "In the Year of the Weaver's Triumph"
- last attempt: 691 prompt tokens at 6.4/s, 324 generated at 2.9/s; whole text 658 s

**German (llm):**

> Im Jahr des verborgenen Webstuhl der Wäberin, 901, senkte sich über unser schönes Land Saryhorde ein dunkler Wolke des Krieges. Die bösen und verräterischen Leute von Keshband, angeführt vom feigsten Tsetseg the Second, wagten es, gegen uns aufzusteigen.
>
> Auf den grünen Feldern von Bortedag trafen unsere tapferen Krieger auf ihre Feinde, nur um verraten zu werden von den Verrätern von Keshband. Unser tapferer Männer fielen, als sie gegen die grausamen Waffen ihrer Feinde angriffen, und hinterließen unsere gefallenen Heldinnen, Chuluun unter ihnen, einen Helden, der für seine Tapferkeit erinnert wird.
>
> Trotz unserer Verluste, hielten wir an unserer Stärke fest. In den Jahren, die folgten, trafen wir uns mit den Keshband auf den Feldern von Steingrube und Bortedag erneut, jedes Mal verloren wir Boden, aber nie unser Mut.
>
> Im Jahr des Triumphes der Wäberin, 911, unser kluger Herrscher Batu, geführt von dem beobachtenden Auge des Bayan the Unseen, schloss Frieden mit den Keshband. Ein bitterer Pfeffer zu essen, aber ein Mittel, um unsere Leute von weiteren Verletzungen zu schützen.
>
> Das ist die Geschichte unserer Leute, die Geschichte unserer Stärke und Unverwüstbarkeit. May Bayan the Unseen continue to watch over us.

**German (argos):**

> Im Jahr des unsichtbaren Webers, 901, kam eine dunkle Wolke des Krieges über unser schönes Land Saryhorde herab. Das abscheuliche und heimtückische Volk von Keshband, angeführt vom feigen Tsetseg dem Zweiten, wagte es, ihre Banner gegen uns zu erheben.
>
> Auf den grünen Feldern von Bortedag trafen unsere tapferen Krieger auf ihre Feinde, nur um durch den Verrat des Keshbands verraten zu werden. Unsere tapferen Männer fielen unter die grausamen Klingen ihrer Feinde und hinterließen unsere gefallenen Champions, Chuluun unter ihnen, ein Held, an den man sich für seinen Mut erinnerte.
>
> Doch obwohl wir Verluste erlitten, sind wir nicht ins Wanken geraten. In den folgenden Jahren trafen wir den Keshband auf den Feldern von Steingrube und Bortedag noch einmal, jedes Mal an Boden verlierend, aber nie unsere Geister.
>
> Im Jahr des Triumphs des Webers, 911, schmiedete unser weiser Herrscher Batu, geführt vom wachsamen Auge von Bayan dem Unsichtbaren Weber, einen Frieden mit dem Keshband. Eine bittere Pille zum Schlucken, aber eine, die unser Volk vor weiterem Blutvergießen bewahrt hat.
>
> So spiegelt sich die Geschichte der Widerstandsfähigkeit unseres Volkes durch die Zeitalter wider, ein Beweis für unseren unerschütterlichen Geist und die Stärke unseres Landes, des Landes, das wir Saryhorde nennen. Möge Bayan, der unsichtbare Weber, weiterhin über uns wachen.

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

> In the annals of our noble Penpike, the year of the Boar, 901, saw the unjust aggression of the distant and barbarous realm of Bortebulak, led by the unworthy Batu. Our valiant forces, under the wise Tsetseg, marched forth to meet them in the fields of Bortedag, where the treacherous Saryhorde had gathered their hordes.
>
> There, in a display of courage unmatched, our warriors, guided by Bayan the Unseen Weaver, bested the enemy and claimed the lives of 406 of their number. The cowardly Batu, unable to withstand our might, was forced to retreat.
>
> Yet, the bloodthirsty Saryhorde again sought battle, this time near our hallowed city of Penpike. On that day, the heavens themselves seemed to conspire against us, for we met with a cruel defeat. Yet, it was not through any failing of our warriors, but rather the treachery of the Saryhorde that led to our temporary setback.
>
> For it was in the year of the Serpent, 906, that the Saryhorde dared to challenge us again, and again we met them in the fields of Bortedag. This time, our numbers were bolstered by the brave forces of our allies, and we emerged victorious, taking the lives of 408 of the enemy. It is said that the cowardly Batu fell in that battle, his life taken by the mighty hand of the Unseen Weaver.
>
> In the year of the Horse, 911, the Saryhorde dared to approach our city once more, but our forces, now strengthened by the lessons learned in battle, were ready. They met us in the

**Validator:** FAILED after 3 attempt(s).
- attempt 1: name "BORTEDAG", name "Unyielding Empire", name "Unyielding Empire", case "BORTEDAG"
- attempt 2: name "The Chronicle of Penpike", name "In the Fields of Steingrube", name "Unyielding Empire"
- attempt 3: name "Boar", name "Serpent", name "Horse"
- last attempt: 711 prompt tokens at 4.9/s, 399 generated at 2.6/s; whole text 840 s

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

> Tsetseg the Second, born 886, died 951. The child of Tsetseg, the ruler of Keshband at Silberscar, maker of the Grassdiadem. May his spirit ride the winds, free as his spirit was.

**Validator:** passed after 2 attempt(s).
- attempt 1: name "Hear the Valiant Tsetseg the Second", name "Barrow of Silberscar", name "Ruler", number "sixty-five"
- last attempt: 705 prompt tokens at 5.5/s, 58 generated at 2.8/s; whole text 288 s

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

> The Barrow of Silberscar.
>
> "Here lies Gerel, child of Khulan,
> Born in the year of the Saryhorde's birth, 686,
> Smith of Silberscar, maker of the Cleaver,
> Died in the year 751, in peace."

**Validator:** FAILED after 3 attempt(s).
- attempt 1: name "Gerel Orkhodag", name "Year of the Raven", name "Year of the Serpent"
- attempt 2: name "Smith of Iron"
- attempt 3: name "The Barrow of Silberscar"
- last attempt: 473 prompt tokens at 6.1/s, 71 generated at 3.1/s; whole text 322 s

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

> Here is your task:
>
> To Grimhold, home of Gerel the Unbeaten, child of Temujin the Exacting,
> Born 541, died 606,
> Hero who slew Skarn, the Mire Mother,
> May his spirit ride the winds of battle.

**Validator:** passed after 3 attempt(s).
- attempt 1: number "65,"
- attempt 2: name "The Barrow of Grimhold"
- last attempt: 481 prompt tokens at 7.6/s, 71 generated at 3.8/s; whole text 242 s

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

> Here lies Gerel, the Burned of Karaobo
> Born in the Saryhorde's year 851
> He who founded the sanctuary in 886
> Departed in the Saryhorde's year 916.

**Validator:** passed after 2 attempt(s).
- attempt 1: name "The Prophet Gerel", name "Burned One", name "Born", name "Fledged", name "Karatau's Voice", name "Sleep", name "Saryhorde's Slumber"
- last attempt: 572 prompt tokens at 7.7/s, 61 generated at 3.9/s; whole text 153 s

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

> Temujin of Saryhorde, scholar of Karatau, born in 856, laid to rest in 921, Farsignet now within the barrow of Karaobo, may his wisdom guide the Saryhorde.

**Validator:** passed after 2 attempt(s).
- attempt 1: number "fifty-six", number "twenty-one"
- last attempt: 428 prompt tokens at 7.9/s, 57 generated at 4.0/s; whole text 130 s

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

> Long ago, in the year 716, a crown was forged at Silberscar by Bayan the Patient, it was named Windiadem. The crown was passed from Gerel to Tsetseg, then to Tsetseg, then to Yesui the Second, then to Chuluun, then to Gerel the Burned. In the year 916, it was given to the temple of Bayan the Unseen Weaver at Karatau. In the year 926, the Bortekhai carried Windiadem off from Karaobo to Grafell, where it remains now, kept in the temple of Grafell.

**Validator:** FAILED after 3 attempt(s).
- attempt 1: name "Windiadem", name "Windgod", number "seven hundred and six"
- attempt 2: name "Windiadem", name "Windiadem", number "seven hundred and six", lowercase "diadem"
- attempt 3: name "Windiadem", name "Windiadem"
- last attempt: 544 prompt tokens at 7.8/s, 143 generated at 3.8/s; whole text 368 s

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

> And so, by the fire, the storyteller speaks,
>
> "Once, in the time of the Iron Age, there lived a blacksmith named Yesui, the Second,
> Of noble house in the land of Bortedag,
> He forged, in the year seven hundred and seventy-six,
> A ring of wrought iron, golden and fine,
> A token of power, a ring of might, the Farsignet.
> In time it passed, from Chuluun, to Gerel, the Burned,
> Then to Temujin, a mighty conquerer, a king.
> In his time of greatness, the ring was laid in his tomb,
> In the year nine two one, on the day of his death.
> And there it remains, in the barrow of Karaobo,
> A symbol of power, a token of the old,
> The Farsignet, forged by Yesui, the blacksmith of Bortedag."

**Validator:** FAILED after 3 attempt(s).
- attempt 1: name "Mongol Khan"
- attempt 2: name "Tengri", name "Iron Chieftain"
- attempt 3: name "Iron Age"
- last attempt: 444 prompt tokens at 7.2/s, 212 generated at 3.6/s; whole text 319 s

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

> In the twilight of the age of iron, 'twas at Highstein that Kunimund, the sagacious smith, fashioned the Hollowcodex, a tome most wondrous, woven from the finest vellum. The wisdom of ages flowed through its pages, whispered secrets of old, and the fires of intuition danced within its boundaries.
>
> In the year 506, the Hollowcodex found its first steward, none other than Kunimund the Reluctant, the shepherd of the land. He was but a humble man, yet the secrets of the Hollowcodex ignited within him a fire he had never known.
>
> The years that followed saw the Hollowcodex passed from hand to hand, its secrets guarded by those who sought to understand the mysteries it held. It reached the hands of Trevanion, the Unquiet, a man consumed by his quest for knowledge. In his final moments, Trevanion laid the Hollowcodex to rest within his tomb, sealing its secrets for eternity.
>
> Now, the tomb of Trevanion the Unquiet stands in the barrow of Grafell, and within it lies the Hollowcodex, its wisdom waiting for the hands of those who seek the secrets of the ages.

**Validator:** passed after 1 attempt(s).
- last attempt: 380 prompt tokens at 7.5/s, 287 generated at 3.9/s; whole text 125 s

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

> Hark, Trevanion the Unseen Weaver,
> Weave the threads and weave our fate,
> In Erzvein's hallowed chamber,
> Your sacred insect guide,
> In your Circle's sacred dance,
> Protect Boshold in our plight.

**Validator:** passed after 1 attempt(s).
- last attempt: 544 prompt tokens at 7.9/s, 61 generated at 3.9/s; whole text 85 s

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

> Erwin of the Black Earth,
> listener in the hollow halls,
> giver of eternal rest,
> by your will we'll lay to rest.
>
> In Steingrube, barren land,
> you bore the deadly blight,
> in your name, we'll stand our stand,
> Erdene of the Black Earth, our plight.
>
> Temple walls whisper your name,
> in your domain, we're but frail,
> yet, in your shadow, we claim
> peaceful solace, no pains or gales.
>
> Your realm is cold, but you,
> In death, grant us our due,
> Eternal peace, let it renew,
> our weary souls, Erwin, renew.

**Validator:** passed after 1 attempt(s).
- last attempt: 427 prompt tokens at 8.1/s, 163 generated at 3.9/s; whole text 96 s

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
