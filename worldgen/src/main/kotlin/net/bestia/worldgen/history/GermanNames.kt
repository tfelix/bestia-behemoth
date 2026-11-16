package net.bestia.worldgen.history

import net.bestia.worldgen.core.BeastKind
import net.bestia.worldgen.core.Sex
import net.bestia.worldgen.core.WorldBoss

/**
 * The German word for every English word [Names] picks, keyed by the English word. [Names] still picks in
 * English, so the German name is the same pick as the English one, with German grammar.
 *
 * Nominative only, "Kunimund der Widerwillige": a sentence that needs "Kunimunds des Widerwilligen" must word
 * itself around the name. A god's title inside a cult's name is the one genitive, because that is how the
 * name itself is built.
 *
 * Not in `Names.catalogueDigest`: a better German word changes no world, since the seeds and the picks stay.
 */
internal object GermanNames {

  fun byname(english: String, sex: Sex): String {
    return BYNAMES.getValue(english).of(sex)
  }

  fun house(stem: String): String {
    return "Haus $stem"
  }

  fun deity(given: String, sex: Sex, englishTitle: String): String {
    return "$given ${DEITY_TITLES.getValue(englishTitle).nominative.of(sex)}"
  }

  /** A god as the object of a cult's name: "der Hilda vom Roten Speer", "des Batu des Ungebeugten". */
  fun deityGenitive(given: String, sex: Sex, englishTitle: String): String {
    val article = if (sex == Sex.FEMALE) "der" else "des"
    return "$article $given ${DEITY_TITLES.getValue(englishTitle).genitive.of(sex)}"
  }

  /** @param of who the cult follows, already in the genitive. */
  fun cult(englishBand: String, of: String): String {
    return "die ${CULT_BANDS.getValue(englishBand)} $of"
  }

  fun beast(kind: BeastKind): String {
    return BEASTS.getValue(kind)
  }

  fun bossTitle(boss: WorldBoss): String {
    return BOSS_TITLES.getValue(boss)
  }

  fun peopleTail(english: String): String {
    return PEOPLE_TAILS.getValue(english)
  }

  fun artifactOfPlace(englishNoun: String, place: String): String {
    return "${ARTIFACT_NOUNS.getValue(englishNoun)} von $place"
  }

  fun artifactCompound(englishEpithet: String, englishNoun: String): String {
    return EPITHETS.getValue(englishEpithet) + ARTIFACT_NOUNS.getValue(englishNoun).asCompoundTail()
  }

  /** "Elmtal", or with an epithet "Alt-Elmtal": German joins a region's head and form into one word. */
  fun region(head: String, englishForm: String, englishEpithet: String?): String {
    val name = head + SITE_FORMS.getValue(englishForm).asCompoundTail()
    return if (englishEpithet == null) name else "${EPITHETS.getValue(englishEpithet)}-$name"
  }

  fun site(englishForm: String, of: String): String {
    return "${SITE_FORMS.getValue(englishForm)} von $of"
  }

  fun wound(englishEpithet: String, englishNoun: String): String {
    val noun = WOUND_NOUNS.getValue(englishNoun)
    return "${noun.article} ${WOUND_EPITHETS.getValue(englishEpithet)}e ${noun.word}"
  }

  /** A word that follows the sex of whoever bears it: "der Widerwillige", "die Widerwillige". */
  internal class Gendered(private val masculine: String, private val feminine: String) {
    fun of(sex: Sex): String {
      return if (sex == Sex.FEMALE) feminine else masculine
    }
  }

  internal class DeityTitle(val nominative: Gendered, val genitive: Gendered)

  internal class Noun(val article: String, val word: String) {
    fun asCompoundTail(): String {
      return word.lowercase()
    }

    override fun toString(): String {
      return "$article $word"
    }
  }

  /** "der Widerwillige": a weak adjective, the same word for both sexes after the article. */
  private fun adjective(stem: String): Gendered {
    return Gendered("der ${stem}e", "die ${stem}e")
  }

  /** "der Gesetzgeber", "die Gesetzgeberin". */
  private fun agent(noun: String): Gendered {
    return Gendered("der $noun", "die ${noun}in")
  }

  /** A byname with no article, or one whose noun keeps its own gender: "Eisenhand", "die Stimme". */
  private fun fixed(text: String): Gendered {
    return Gendered(text, text)
  }

  private fun prepositionTitle(text: String): DeityTitle {
    return DeityTitle(fixed(text), fixed(text))
  }

  private fun adjectiveTitle(stem: String): DeityTitle {
    return DeityTitle(adjective(stem), Gendered("des ${stem}en", "der ${stem}en"))
  }

  /** A title with no article, which stays the same in the genitive: "des Batu Donnerhand". */
  private fun bareTitle(masculine: String, feminine: String = masculine): DeityTitle {
    return DeityTitle(Gendered(masculine, feminine), Gendered(masculine, feminine))
  }

  private fun bareAgentTitle(noun: String): DeityTitle {
    return bareTitle(noun, "${noun}in")
  }

  private fun agentTitle(noun: String): DeityTitle {
    return DeityTitle(agent(noun), Gendered("des ${noun}s", "der ${noun}in"))
  }

  private fun title(
    masculine: String,
    feminine: String,
    masculineGenitive: String,
    feminineGenitive: String
  ): DeityTitle {
    return DeityTitle(Gendered(masculine, feminine), Gendered(masculineGenitive, feminineGenitive))
  }

  internal val BYNAMES: Map<String, Gendered> = mapOf(
    "the Elder" to adjective("Älter"),
    "the Lawgiver" to agent("Gesetzgeber"),
    "the Unquiet" to adjective("Ruhelos"),
    "the Wide-Handed" to adjective("Freigebig"),
    "the Second" to adjective("Zweit"),
    "the Reluctant" to adjective("Widerwillig"),
    "the Grey" to adjective("Grau"),
    "the Cruel" to adjective("Grausam"),

    "the Spear" to fixed("der Speer"),
    "Ironhand" to fixed("Eisenhand"),
    "the Unbeaten" to adjective("Unbezwungen"),
    "the Bloody" to adjective("Blutig"),
    "the Watchful" to adjective("Wachsam"),
    "One-Eye" to fixed("Einauge"),
    "the Bridge-Breaker" to agent("Brückenbrecher"),

    "the Voice" to fixed("die Stimme"),
    "the Barefoot" to adjective("Barfüßig"),
    "the Burned" to adjective("Verbrannt"),
    "the Sleepless" to adjective("Schlaflos"),
    "the Weeper" to adjective("Weinend"),
    "the Silent" to adjective("Schweigsam"),

    "the Quenched" to adjective("Abgeschreckt"),
    "Blackthumb" to fixed("Schwarzdaumen"),
    "the Patient" to adjective("Geduldig"),
    "Nine-Fingers" to fixed("Neunfinger"),
    "the Exacting" to adjective("Genau"),
    "Coalbeard" to fixed("Kohlenbart"),

    "the Far-Walked" to adjective("Weitgereist"),
    "the Lost" to adjective("Verschollen"),
    "Saltbitten" to adjective("Salzgegerbt"),
    "the Returned" to adjective("Heimgekehrt"),
    "the Mapmaker" to agent("Kartenzeichner"),

    "the Annalist" to agent("Chronist"),
    "the Doubter" to agent("Zweifler"),
    "Inkhand" to fixed("Tintenhand"),
    "the Tiresome" to adjective("Lästig"),
    "the Counter" to agent("Zähler")
  )

  /**
   * A title that names a father or a mother follows the god's sex, "Weizenvater" or "Weizenmutter": every other
   * German title already agrees with the god, and one that did not would read as a mistake.
   */
  internal val DEITY_TITLES: Map<String, DeityTitle> = mapOf(
    "of the Red Spear" to prepositionTitle("vom Roten Speer"),
    "the Unbowed" to adjectiveTitle("Ungebeugt"),
    "Shieldbreaker" to bareAgentTitle("Schildbrecher"),

    "the Fair" to adjectiveTitle("Schön"),
    "of the Rose" to prepositionTitle("von der Rose"),
    "Heartkeeper" to bareAgentTitle("Herzhüter"),

    "the Gatekeeper" to agentTitle("Torwächter"),
    "of the Black Earth" to prepositionTitle("von der Schwarzen Erde"),
    "the Last Host" to title(
      "der Letzte Gastgeber", "die Letzte Gastgeberin", "des Letzten Gastgebers", "der Letzten Gastgeberin"
    ),

    "the Bright" to adjectiveTitle("Strahlend"),
    "of the Golden Disc" to prepositionTitle("von der Goldenen Scheibe"),
    "Dawnbringer" to bareAgentTitle("Morgenbringer"),

    "the Pale" to adjectiveTitle("Bleich"),
    "of the Silver Hare" to prepositionTitle("vom Silbernen Hasen"),
    "Tidewatcher" to bareAgentTitle("Gezeitenwächter"),

    "Thunderhand" to bareTitle("Donnerhand"),
    "of the Oak" to prepositionTitle("von der Eiche"),
    "the Stormlord" to title("der Sturmherr", "die Sturmherrin", "des Sturmherrn", "der Sturmherrin"),

    "of the Deep" to prepositionTitle("aus der Tiefe"),
    "Wavecaller" to bareAgentTitle("Wellenrufer"),
    "the Drowned King" to title(
      "der Ertrunkene König", "die Ertrunkene Königin", "des Ertrunkenen Königs", "der Ertrunkenen Königin"
    ),

    "Mother of Wheat" to bareTitle("Weizenvater", "Weizenmutter"),
    "of the Sheaf" to prepositionTitle("von der Garbe"),
    "the Green" to adjectiveTitle("Grün"),

    "of the Undying Fire" to prepositionTitle("vom Ewigen Feuer"),
    "Keeper of Doors" to bareAgentTitle("Türhüter"),
    "Breadgiver" to bareAgentTitle("Brotgeber"),

    "the Owl-Eyed" to adjectiveTitle("Eulenäugig"),
    "of the Scroll" to prepositionTitle("von der Schriftrolle"),
    "the Far-Seeing" to adjectiveTitle("Weitblickend"),

    "of the Anvil" to prepositionTitle("vom Amboss"),
    "the Forgefather" to title("der Schmiedevater", "die Schmiedemutter", "des Schmiedevaters", "der Schmiedemutter"),
    "Hammerhand" to bareTitle("Hammerhand"),

    "of the Crossroads" to prepositionTitle("vom Kreuzweg"),
    "the Wayfarer" to title("der Wanderer", "die Wanderin", "des Wanderers", "der Wanderin"),
    "Coinkeeper" to bareAgentTitle("Münzhüter"),

    "the Masked" to adjectiveTitle("Maskiert"),
    "of Many Faces" to prepositionTitle("der Vielen Gesichter"),
    "the Laughing One" to adjectiveTitle("Lachend"),

    "of the Spring" to prepositionTitle("von der Quelle"),
    "the Mender" to agentTitle("Heiler"),
    "Snakebearer" to bareAgentTitle("Schlangenträger"),

    "of the Antlers" to prepositionTitle("vom Geweih"),
    "the Swift" to adjectiveTitle("Flink"),
    "Houndmaster" to bareAgentTitle("Hundemeister"),

    "the Spinner" to adjectiveTitle("Spinnend"),
    "of the Thread" to prepositionTitle("vom Faden"),
    "the Unseen Weaver" to title(
      "der Unsichtbare Weber", "die Unsichtbare Weberin", "des Unsichtbaren Webers", "der Unsichtbaren Weberin"
    ),

    "of the Scales" to prepositionTitle("von der Waage"),
    "the Judge" to agentTitle("Richter"),
    "Truthkeeper" to bareAgentTitle("Wahrheitshüter"),

    "of the Vine" to prepositionTitle("von der Rebe"),
    "the Reveller" to agentTitle("Zecher"),
    "Cupbearer" to bareTitle("Mundschenk", "Mundschenkin"),

    "of the Veil" to prepositionTitle("vom Schleier"),
    "the Dreamer" to agentTitle("Träumer"),
    "Starmother" to bareTitle("Sternenvater", "Sternenmutter")
  )

  /** Every band is a plural or a feminine noun, so every cult's name starts with "die". */
  internal val CULT_BANDS: Map<String, String> = mapOf(
    "Children" to "Kinder", "Faithful" to "Getreuen", "Brotherhood" to "Bruderschaft",
    "Sisterhood" to "Schwesternschaft", "Keepers" to "Hüter", "Chosen" to "Erwählten"
  )

  /** Whom a cult follows that is no god, in the genitive its name needs. */
  const val NAMELESS = "des Namenlosen"
  const val FALLEN_STAR = "des Gefallenen Sterns"

  internal val BEASTS: Map<BeastKind, String> = mapOf(
    BeastKind.BEAST to "der Bestie", BeastKind.BIRD to "der Schwinge", BeastKind.REPTILE to "der Schuppe",
    BeastKind.AQUATIC to "der Tiefe", BeastKind.INSECT to "des Schwarms", BeastKind.PLANT to "des Grüns",
    BeastKind.FORMLESS to "des Formlosen", BeastKind.ELEMENTAL to "des Lebenden Steins",
    BeastKind.UNDEAD to "der Ruhelosen Toten", BeastKind.DEMON to "des Gehörnten", BeastKind.DRAGON to "des Lindwurms"
  )

  internal val BOSS_TITLES: Map<WorldBoss, String> = mapOf(
    WorldBoss.OSSUARY_WYRM to "der Knochenlindwurm",
    WorldBoss.LEVIATHAN to "der Leviathan",
    WorldBoss.BEHEMOTH to "der Behemoth",
    WorldBoss.EMBER_SOVEREIGN to "der Glutherrscher",
    WorldBoss.MIRE_MOTHER to "die Sumpfmutter"
  )

  /** A people's name ends in what its people are called: "Elminger", "Karareiter". Words of no language stay. */
  internal val PEOPLE_TAILS: Map<String, String> = mapOf(
    "ings" to "inger", "folk" to "volk", "shire" to "gauer", "marches" to "märker", "vale" to "taler",
    "mennir" to "mennir", "kin" to "sippe", "sund" to "sunder", "reach" to "länder",
    "khai" to "khai", "ulus" to "ulus", "horde" to "horde", "band" to "schar", "riders" to "reiter",
    "volk" to "volk", "guild" to "gilde", "hold" to "hold", "delvers" to "gräber", "clans" to "sippen"
  )

  /** As the first half of a compound: "Sturmzahn", "Alt-Elmtal". */
  internal val EPITHETS: Map<String, String> = mapOf(
    "green" to "Grün", "harvest" to "Ernte", "elder" to "Alt", "long" to "Lang", "still" to "Still",
    "storm" to "Sturm", "salt" to "Salz", "winter" to "Winter", "tide" to "Gezeiten", "gale" to "Orkan",
    "sky" to "Himmels", "wind" to "Wind", "far" to "Fern", "swift" to "Flink", "grass" to "Gras",
    "iron" to "Eisen", "deep" to "Tief", "grim" to "Grimm", "under" to "Unter", "hollow" to "Hohl"
  )

  internal val ARTIFACT_NOUNS: Map<String, Noun> = mapOf(
    "brand" to Noun("die", "Klinge"), "edge" to Noun("die", "Schneide"), "fang" to Noun("der", "Zahn"),
    "sabre" to Noun("der", "Säbel"), "cleaver" to Noun("der", "Spalter"), "spike" to Noun("der", "Dorn"),

    "crown" to Noun("die", "Krone"), "circlet" to Noun("der", "Reif"), "diadem" to Noun("das", "Diadem"),
    "helm" to Noun("der", "Helm"), "torc" to Noun("der", "Torques"),

    "reliquary" to Noun("das", "Reliquiar"), "casket" to Noun("die", "Schatulle"), "shrine" to Noun("der", "Schrein"),
    "urn" to Noun("die", "Urne"), "coffer" to Noun("die", "Truhe"),

    "codex" to Noun("der", "Kodex"), "annal" to Noun("die", "Chronik"), "testament" to Noun("das", "Testament"),
    "ledger" to Noun("das", "Buch"), "scroll" to Noun("die", "Schriftrolle"),

    "ring" to Noun("der", "Ring"), "band" to Noun("das", "Band"), "seal" to Noun("das", "Siegel"),
    "signet" to Noun("der", "Siegelring"),

    "banner" to Noun("das", "Banner"), "standard" to Noun("die", "Standarte"), "pennon" to Noun("der", "Wimpel"),
    "colours" to Noun("die", "Farben")
  )

  /** The forms of sites and regions. A region joins the noun into its own name: "das Tal" makes "Elmtal". */
  internal val SITE_FORMS: Map<String, Noun> = mapOf(
    "ruin" to Noun("die", "Ruinen"), "ash" to Noun("die", "Asche"), "battlefield" to Noun("das", "Schlachtfeld"),
    "barrow" to Noun("das", "Hügelgrab"), "monument" to Noun("das", "Denkmal"), "mine" to Noun("das", "Bergwerk"),
    "abbey" to Noun("die", "Abtei"), "fort" to Noun("die", "Feste"), "light" to Noun("das", "Leuchtfeuer"),
    "hoard" to Noun("der", "Hort"), "sanctuary" to Noun("das", "Heiligtum"), "seal" to Noun("das", "Siegel"),
    "lair" to Noun("der", "Bau"), "cairn" to Noun("das", "Steinmal"), "ward" to Noun("die", "Wacht"),
    "circle" to Noun("der", "Steinkreis"), "shrine" to Noun("der", "Schrein"),

    "vale" to Noun("das", "Tal"), "downs" to Noun("die", "Höhen"), "fells" to Noun("die", "Berge"),
    "march" to Noun("die", "Mark"), "waste" to Noun("die", "Öde"), "moor" to Noun("das", "Moor"),
    "wood" to Noun("der", "Wald"), "coast" to Noun("die", "Küste"), "sound" to Noun("der", "Sund"),
    "deep" to Noun("die", "Tiefe")
  )

  /** Stems of weak adjectives: "die Versunkene Wunde". */
  internal val WOUND_EPITHETS: Map<String, String> = mapOf(
    "Sunken" to "Versunken", "Bitter" to "Bitter", "Riven" to "Zerrissen", "Weeping" to "Weinend",
    "Silent" to "Schweigend", "Hollow" to "Hohl", "Starless" to "Sternenlos", "Pale" to "Bleich",
    "Fallen" to "Gefallen", "Ashen" to "Aschfahl"
  )

  internal val WOUND_NOUNS: Map<String, Noun> = mapOf(
    "Wound" to Noun("die", "Wunde"), "Scar" to Noun("die", "Narbe"), "Hollow" to Noun("die", "Senke"),
    "Fall" to Noun("der", "Absturz"), "Waste" to Noun("die", "Öde"), "Crater" to Noun("der", "Krater")
  )
}
