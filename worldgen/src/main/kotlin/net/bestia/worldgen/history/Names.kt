package net.bestia.worldgen.history

import net.bestia.worldgen.core.ArtifactKind
import net.bestia.worldgen.core.BeastKind
import net.bestia.worldgen.core.CultKind
import net.bestia.worldgen.core.DeityRecord
import net.bestia.worldgen.core.Domain
import net.bestia.worldgen.core.Faction
import net.bestia.worldgen.core.FigureRole
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.Sex
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.WorldBoss

/**
 * Deterministic names, reconstructed from a seed rather than stored.
 *
 * ### Why a seed and not a string
 *
 * The vector tier carries `Double` station channels and nothing else, so a `RUIN` marker cannot hold the
 * name of the town it used to be. It can hold a 48-bit integer, and that integer plus this file is the
 * name - which is the same trick the architecture document proposes for building grammars, applied to
 * text. The consequences are worth stating because they are not obvious:
 *
 * - A name costs eight bytes wherever it is mentioned, so a settlement, its ruin, its tomb and every
 *   event about it can all carry it without storing four copies of a string.
 * - Any tool can print any name with no lookup table and no access to the chronicle.
 * - **Changing this file renames the entire world.** That is why the seed is derived from the entity and
 *   the world seed rather than from a counter: renaming is then at least *stable* under a re-run, and a
 *   change here is a cosmetic diff rather than a silent history rewrite. It is still a change nobody
 *   should make after a world ships.
 *
 * Names are only ever *displayed*, never compared or used as keys, so two entities colliding on one is a
 * cosmetic coincidence rather than a correctness problem. At 48 bits it happens about once in sixteen
 * million, which for a few thousand named things is never.
 */
object Names {

  /** Mask that keeps a name seed exactly representable in a station channel. See [Names]. */
  const val SEED_MASK = 0xFFFF_FFFF_FFFFL

  /** A name seed for an entity, from the world seed and whatever identifies it. */
  fun seedOf(worldSeed: Long, vararg key: Long): Long =
    GenRng.hash(worldSeed, *key) and SEED_MASK

  /**
   * A settlement name.
   *
   * Built as prefix + optional link + suffix, where the suffixes are the ones that mean something -
   * "-ford", "-holm", "-fell" - so a name says a little about the place even before it is looked at. The
   * culture picks the pools, which is what makes a seafaring coast read differently from a highland one.
   */
  fun place(seed: Long, cultureIndex: Int): String {
    val style = styleOf(cultureIndex)
    val stem = pick(style.placeStems, seed, 1)
    val tail = pick(style.placeTails, seed, 2)

    // A double consonant where the stem ends and the tail begins reads as a typo rather than as a name.
    val joined = if (stem.last() == tail.first()) stem + tail.drop(1) else stem + tail
    return joined.replaceFirstChar { it.uppercase() }
  }

  /**
   * A region name: "Elm Downs", "Iron Fells", "Salt Sound".
   *
   * Stem plus the region's own form word, and the stem comes from the same [Style.placeStems] the towns
   * inside it draw on. A region and a settlement landing on one stem is not a collision to be avoided -
   * "Elmford in the Elm Downs" reads as one language, and arguably as one etymology.
   *
   * @param cultureIndex the culture that named it, or negative where nobody lives near enough to have.
   *   An absent culture picks one off the seed rather than falling through [styleOf]'s clamp to the
   *   first: the clamp would name every empty quarter of the world like a farming valley, and a
   *   wilderness named in an arbitrary idiom at least varies the way real exonyms do. This is the same
   *   problem [site]'s `wound` branch solves, one step less drastically - a region is somewhere people
   *   pass through, so it gets a language rather than an epithet.
   * @param epithet widens the name space with a leading adjective - "Elder Elm Downs", in German "Alt-Elmhöhen".
   *
   *   Twenty stems and ten forms is two hundred names per culture, and regions competing for one form
   *   in one culture see only the twenty. A hundred and eighty regions therefore *will* collide, and
   *   that is not a reason to add a word pool: real toponymy disambiguates the same way, and the
   *   [Style.epithets] this reuses already exist. Callers should only reach for it on an actual clash -
   *   a world where every region has an adjective reads as generated.
   */
  fun region(
    seed: Long,
    cultureIndex: Int,
    form: String,
    epithet: Boolean = false,
    locale: NameLocale = NameLocale.EN
  ): String {
    val culture = if (cultureIndex >= 0) {
      cultureIndex
    } else {
      (GenRng.hash(seed, REGION_CULTURE_SALT) ushr 1).mod(STYLES.size.toLong()).toInt()
    }
    val style = styleOf(culture)

    // A bare stem or a whole place-word, on a coin flip. [artifact] makes this argument first and it is
    // the same one: a world where every region is "<Stem> <Form>" reads as generated, and half the stems
    // in a pool are name fragments rather than words - "Elm Downs" carries itself, "Sube Deep" does not.
    // It also widens the pool from twenty names per culture to several hundred, which is what stops
    // ninety ocean regions colliding on two form words.
    val head = if (unit(seed, REGION_SHAPE_SALT) < 0.5) {
      pick(style.placeStems, seed, REGION_STEM_SALT)
    } else {
      place(seed, culture).lowercase()
    }

    val epithetWord = if (epithet) pick(style.epithets, seed, REGION_EPITHET_SALT) else null

    return when (locale) {
      NameLocale.EN -> {
        val qualified = if (epithetWord != null) "$epithetWord $head" else head
        "${qualified.replaceFirstChar { it.uppercase() }} ${form.replaceFirstChar { it.uppercase() }}"
      }

      NameLocale.DE -> GermanNames.region(head.replaceFirstChar { it.uppercase() }, form, epithetWord)
    }
  }

  /** A personal name, plus a byname often enough that a chronicle does not read as a list of nouns. */
  fun person(seed: Long, cultureIndex: Int, role: FigureRole, locale: NameLocale = NameLocale.EN): String {
    val given = givenName(seed, cultureIndex, PERSON_GIVEN_SALT)
    if (unit(seed, 4) >= 0.55) return given.capitalised()

    val byname = pick(BYNAMES[role] ?: BYNAMES.getValue(FigureRole.RULER), seed, 5)
    return when (locale) {
      NameLocale.EN -> "${given.capitalised()} $byname"
      NameLocale.DE -> "${given.capitalised()} ${GermanNames.byname(byname, given.sex)}"
    }
  }

  /** The sex of the figure [person] names, which follows from its given name. */
  fun personSex(seed: Long, cultureIndex: Int): Sex {
    return givenName(seed, cultureIndex, PERSON_GIVEN_SALT).sex
  }

  private const val PERSON_GIVEN_SALT = 3L

  /**
   * An ordinary person's name: a given name and nothing else.
   *
   * [person]'s bynames are earned - "the Grim", "the Wise" - and belong to the handful of figures a
   * chronicle names. A village has hundreds of people in it and none of them are notable, so they get
   * what most people have ever had. Two of them sharing a name is not a collision to avoid either; a pool
   * of fourteen per culture is how a real village sounds.
   *
   * Reuses [Style.givenNames] rather than adding a pool, so this renames nothing that already exists.
   */
  fun townsperson(seed: Long, cultureIndex: Int): String {
    return givenName(seed, cultureIndex, TOWNSPERSON_SALT).capitalised()
  }

  private const val TOWNSPERSON_SALT = 0x90L

  /** A ruling family, named the way a seat is: "House Ashford". From the place pools, so no pool is added. */
  fun house(seed: Long, cultureIndex: Int, locale: NameLocale = NameLocale.EN): String {
    return when (locale) {
      NameLocale.EN -> "House ${houseStem(seed, cultureIndex)}"
      NameLocale.DE -> GermanNames.house(houseStem(seed, cultureIndex))
    }
  }

  /** The house's own name without the English "House", for text that is translated around it. */
  fun houseStem(seed: Long, cultureIndex: Int): String {
    val style = styleOf(cultureIndex)
    return (pick(style.placeStems, seed, HOUSE_STEM_SALT) + pick(style.placeTails, seed, HOUSE_TAIL_SALT))
      .replaceFirstChar { it.uppercase() }
  }

  private const val HOUSE_STEM_SALT = 0xA0L
  private const val HOUSE_TAIL_SALT = 0xA1L

  /**
   * A god's name: a given name in the culture's style and a title of the god's domain, "Hilda of the Red Spear".
   * The same seed in another culture gives another given name and the same title, so two peoples call one god by
   * two names and both can tell it is the same god.
   */
  fun deity(seed: Long, cultureIndex: Int, domain: Domain, locale: NameLocale = NameLocale.EN): String {
    val given = givenName(seed, cultureIndex, DEITY_GIVEN_SALT)
    val title = pick(DEITY_TITLES.getValue(domain), seed, DEITY_TITLE_SALT)
    return when (locale) {
      NameLocale.EN -> "${given.capitalised()} $title"
      NameLocale.DE -> GermanNames.deity(given.capitalised(), given.sex, title)
    }
  }

  /** The sex of the god [deity] names in this culture. Another people may call it by a name of the other sex. */
  fun deitySex(seed: Long, cultureIndex: Int): Sex {
    return givenName(seed, cultureIndex, DEITY_GIVEN_SALT).sex
  }

  /** A cult's name: "the Children of Hilda of the Red Spear", "the Keepers of the Fallen Star". */
  fun cult(seed: Long, kind: CultKind, deityName: String?, beast: BeastKind?): String {
    val band = pick(CULT_BANDS, seed, CULT_BAND_SALT)
    val of = when (kind) {
      CultKind.DEITY -> deityName ?: "the Nameless"
      CultKind.STAR -> "the Fallen Star"
      CultKind.BEAST -> "the ${BEAST_NOUNS.getValue(beast ?: BeastKind.BEAST)}"
    }
    return "the $band of $of"
  }

  /**
   * A cult's name in [locale], for a cult of [deity] founded among [cultureIndex].
   *
   * Takes the god rather than its rendered name, because German puts the god in the genitive:
   * "die Kinder der Hilda vom Roten Speer".
   */
  fun cult(
    seed: Long,
    kind: CultKind,
    deity: DeityRecord?,
    cultureIndex: Int,
    beast: BeastKind?,
    locale: NameLocale
  ): String {
    return when (locale) {
      NameLocale.EN -> cult(seed, kind, deity?.let { deity(it.nameSeed, cultureIndex, it.domain) }, beast)
      NameLocale.DE -> {
        val of = when (kind) {
          CultKind.DEITY -> deity?.let { germanDeityGenitive(it, cultureIndex) } ?: GermanNames.NAMELESS
          CultKind.STAR -> GermanNames.FALLEN_STAR
          CultKind.BEAST -> GermanNames.beast(beast ?: BeastKind.BEAST)
        }
        GermanNames.cult(pick(CULT_BANDS, seed, CULT_BAND_SALT), of)
      }
    }
  }

  private fun germanDeityGenitive(deity: DeityRecord, cultureIndex: Int): String {
    val given = givenName(deity.nameSeed, cultureIndex, DEITY_GIVEN_SALT)
    val title = pick(DEITY_TITLES.getValue(deity.domain), deity.nameSeed, DEITY_TITLE_SALT)
    return GermanNames.deityGenitive(given.capitalised(), given.sex, title)
  }

  /** A world boss's name: a name of its own and its species' title, "Gharzul, the Bone Wyrm". */
  fun monster(seed: Long, boss: WorldBoss, locale: NameLocale = NameLocale.EN): String {
    val name = pick(MONSTER_NAMES, seed, MONSTER_NAME_SALT)
    return when (locale) {
      NameLocale.EN -> "$name, ${boss.title}"
      NameLocale.DE -> "$name, ${GermanNames.bossTitle(boss)}"
    }
  }

  private const val MONSTER_NAME_SALT = 0xB3L

  private val MONSTER_NAMES = listOf(
    "Gharzul", "Vorthak", "Skarn", "Ulmog", "Thessra", "Morvain", "Kragath", "Ysolde", "Brannoch", "Xerith",
    "Odrak", "Sevrine"
  )

  private const val DEITY_GIVEN_SALT = 0xB0L
  private const val DEITY_TITLE_SALT = 0xB1L
  private const val CULT_BAND_SALT = 0xB2L

  internal val DEITY_TITLES = mapOf(
    Domain.WAR to listOf("of the Red Spear", "the Unbowed", "Shieldbreaker"),
    Domain.LOVE to listOf("the Fair", "of the Rose", "Heartkeeper"),
    Domain.DEATH to listOf("the Gatekeeper", "of the Black Earth", "the Last Host"),
    Domain.SUN to listOf("the Bright", "of the Golden Disc", "Dawnbringer"),
    Domain.MOON to listOf("the Pale", "of the Silver Hare", "Tidewatcher"),
    Domain.SKY to listOf("Thunderhand", "of the Oak", "the Stormlord"),
    Domain.SEA to listOf("of the Deep", "Wavecaller", "the Drowned King"),
    Domain.HARVEST to listOf("Mother of Wheat", "of the Sheaf", "the Green"),
    Domain.HEARTH to listOf("of the Undying Fire", "Keeper of Doors", "Breadgiver"),
    Domain.WISDOM to listOf("the Owl-Eyed", "of the Scroll", "the Far-Seeing"),
    Domain.CRAFT to listOf("of the Anvil", "the Forgefather", "Hammerhand"),
    Domain.TRADE to listOf("of the Crossroads", "the Wayfarer", "Coinkeeper"),
    Domain.TRICKERY to listOf("the Masked", "of Many Faces", "the Laughing One"),
    Domain.HEALING to listOf("of the Spring", "the Mender", "Snakebearer"),
    Domain.HUNT to listOf("of the Antlers", "the Swift", "Houndmaster"),
    Domain.FATE to listOf("the Spinner", "of the Thread", "the Unseen Weaver"),
    Domain.JUSTICE to listOf("of the Scales", "the Judge", "Truthkeeper"),
    Domain.REVELRY to listOf("of the Vine", "the Reveller", "Cupbearer"),
    Domain.NIGHT to listOf("of the Veil", "the Dreamer", "Starmother")
  )

  internal val CULT_BANDS = listOf("Children", "Faithful", "Brotherhood", "Sisterhood", "Keepers", "Chosen")

  internal val BEAST_NOUNS = mapOf(
    BeastKind.BEAST to "Beast", BeastKind.BIRD to "Wing", BeastKind.REPTILE to "Scale", BeastKind.AQUATIC to "Deep",
    BeastKind.INSECT to "Swarm", BeastKind.PLANT to "Green", BeastKind.FORMLESS to "Shapeless",
    BeastKind.ELEMENTAL to "Living Stone", BeastKind.UNDEAD to "Restless Dead", BeastKind.DEMON to "Horned One",
    BeastKind.DRAGON to "Wyrm"
  )

  /** A civilisation's name: a people, not a place, so it takes the demonym form. */
  fun civ(seed: Long, cultureIndex: Int, locale: NameLocale = NameLocale.EN): String {
    val style = styleOf(cultureIndex)
    val tail = pick(style.peopleTails, seed, 7)
    val localTail = when (locale) {
      NameLocale.EN -> tail
      NameLocale.DE -> GermanNames.peopleTail(tail)
    }
    return (pick(style.placeStems, seed, 6) + localTail).replaceFirstChar { it.uppercase() }
  }

  /**
   * An artifact name: "the Hammer of Ashfell", "Winterbrand".
   *
   * Two forms rather than one, because a world where every artifact is "the X of Y" reads as generated and a
   * world where none of them are loses the place names that make a provenance chain feel located.
   *
   * @param ofPlaceSeed name seed of the place it was made, or 0 for a name with no place in it.
   *
   * A *seed* rather than a rendered place name, and that is not fussiness. The first version took the name as
   * a string, which meant every caller had to know where the artifact was forged - and the chronicle tool did
   * not, so it passed null and the same sword appeared as "Longcrown" in one view and "the Crown of Hartford"
   * in another. A name that depends on what the caller happens to know is not a name.
   */
  fun artifact(
    seed: Long,
    cultureIndex: Int,
    kind: ArtifactKind,
    ofPlaceSeed: Long,
    locale: NameLocale = NameLocale.EN
  ): String {
    val noun = pick(ARTIFACT_NOUNS.getValue(kind), seed, 8)

    return if (ofPlaceSeed != 0L && unit(seed, 9) < 0.5) {
      val place = place(ofPlaceSeed, cultureIndex)
      when (locale) {
        NameLocale.EN -> "the ${noun.replaceFirstChar { it.uppercase() }} of $place"
        NameLocale.DE -> GermanNames.artifactOfPlace(noun, place)
      }
    } else {
      val epithet = pick(styleOf(cultureIndex).epithets, seed, 10)
      when (locale) {
        NameLocale.EN -> "$epithet$noun".replaceFirstChar { it.uppercase() }
        NameLocale.DE -> GermanNames.artifactCompound(epithet, noun)
      }
    }
  }

  /**
   * A site name, which is nearly always possessive: "the barrow of X", "Y Battlefield".
   *
   * Culture-independent: [of] already carries whatever a settlement's own name owes to its culture, and a
   * `form` like "barrow" or "battlefield" is a description of the site rather than an idiom belonging to anyone.
   *
   * German puts [of] after "von", so it must read in the dative as it stands: a town or a people does, a world
   * boss with its title ("Gharzul, der Knochenlindwurm") does not.
   */
  fun site(seed: Long, of: String, form: String, locale: NameLocale = NameLocale.EN): String {
    if (form == "wound") return wound(seed, locale)

    return when (locale) {
      NameLocale.EN -> when (form) {
        "barrow" -> "the barrow of $of"
        "battlefield" -> "$of Battlefield"
        "ruin" -> "the ruins of $of"
        else -> "the $form of $of"
      }

      NameLocale.DE -> GermanNames.site(form, of)
    }
  }

  /**
   * A wound belongs to nobody, so it cannot be named after a town like everything else here.
   *
   * `site`'s `else` branch rendered it "the wound of the wilds" - and it did, for all three of them, in the
   * same year, in a chronicle that then referred to each of them by that name for a thousand years. A site the
   * log keeps naming has to be distinguishable, so this one gets an epithet off its own seed.
   */
  private fun wound(seed: Long, locale: NameLocale): String {
    val epithet = pick(WOUND_EPITHETS, seed, WOUND_SALT)
    val noun = pick(WOUND_NOUNS, seed, WOUND_NOUN_SALT)
    return when (locale) {
      NameLocale.EN -> "the $epithet $noun"
      NameLocale.DE -> GermanNames.wound(epithet, noun)
    }
  }

  /** The form word [site] names a site of [kind] by. A shrine's follows its Order: [faction]. */
  fun siteForm(kind: SiteKind, faction: Faction?): String {
    return when (kind) {
      SiteKind.RUIN -> "ruin"
      SiteKind.ASH_RUIN -> "ash"
      SiteKind.BATTLEFIELD -> "battlefield"
      SiteKind.TOMB -> "barrow"
      SiteKind.MONUMENT -> "monument"
      SiteKind.MINE -> "mine"
      SiteKind.MONASTERY -> "abbey"
      SiteKind.FORT -> "fort"
      SiteKind.LIGHTHOUSE -> "light"
      SiteKind.HOARD -> "hoard"
      SiteKind.WOUND -> "wound"
      SiteKind.SANCTUARY -> "sanctuary"
      SiteKind.SEAL -> "seal"
      SiteKind.LAIR -> "lair"
      // Three forms from one kind. "The cairn of Ashford" and "the ward of Ashford" are different places to a
      // player, and neither needed a `SiteKind` or a word pool of its own.
      SiteKind.SHRINE -> when (faction) {
        Faction.CHAOS -> "cairn"
        Faction.ETERNITY -> "ward"
        Faction.CIRCLE -> "circle"
        // Unreachable: `raiseShrines` is the only producer and it always sets one. Named rather than
        // thrown because a name is not worth failing a world's generation over.
        null -> "shrine"
      }
    }
  }

  /**
   * The words a wound gets instead of an owner's name. Culture-independent, deliberately.
   *
   * Every other name in this file is drawn from a per-culture pool, because a place is named by the people who
   * live near it. A wound predates all of them and often has nobody within twenty kilometres of it, so naming it
   * in one culture's idiom would be a claim the world does not support. These are what anybody would call it.
   */
  internal val WOUND_EPITHETS = listOf(
    "Sunken", "Bitter", "Riven", "Weeping", "Silent", "Hollow", "Starless", "Pale", "Fallen", "Ashen"
  )

  internal val WOUND_NOUNS = listOf("Wound", "Scar", "Hollow", "Fall", "Waste", "Crater")

  private const val WOUND_SALT = 0x77L
  private const val WOUND_NOUN_SALT = 0x78L

  private const val REGION_STEM_SALT = 0x80L
  private const val REGION_CULTURE_SALT = 0x81L
  private const val REGION_EPITHET_SALT = 0x82L
  private const val REGION_SHAPE_SALT = 0x83L

  private fun styleOf(cultureIndex: Int): Style = STYLES[cultureIndex.coerceIn(0, STYLES.size - 1)]

  private fun <T> pick(pool: List<T>, seed: Long, salt: Long): T =
    pool[(GenRng.hash(seed, salt) ushr 1).mod(pool.size.toLong()).toInt()]

  private fun givenName(seed: Long, cultureIndex: Int, salt: Long): GivenName {
    return pick(styleOf(cultureIndex).givenNames, seed, salt)
  }

  internal fun givenNamesOf(cultureIndex: Int): List<GivenName> {
    return styleOf(cultureIndex).givenNames
  }

  private fun unit(seed: Long, salt: Long): Double = GenRng.hashUnit(seed, salt)

  /** The word pools one culture draws on. */
  internal class Style(
    val placeStems: List<String>,
    val placeTails: List<String>,
    val peopleTails: List<String>,
    val givenNames: List<GivenName>,
    val epithets: List<String>
  )

  /** A given name and the sex of whoever bears it, so a person's sex follows from the name they already have. */
  internal data class GivenName(val name: String, val sex: Sex) {
    fun capitalised(): String {
      return name.replaceFirstChar { it.uppercase() }
    }
  }

  private fun female(name: String): GivenName {
    return GivenName(name, Sex.FEMALE)
  }

  private fun male(name: String): GivenName {
    return GivenName(name, Sex.MALE)
  }

  /**
   * One pool set per [net.bestia.worldgen.civ.Culture], in the same order as `Culture.ALL`.
   *
   * Indexed by culture rather than looked up by name so that adding a culture is a compile-time hole here
   * - `STYLES[index]` would silently reuse the last style otherwise, and every new culture would be named
   * like a highland one.
   */
  internal val STYLES = listOf(
    // Agrarian: English river-valley farming names.
    Style(
      placeStems = listOf(
        "ash", "elm", "wheat", "barley", "mill", "wood", "green", "long", "black", "old",
        "stan", "brad", "chad", "hal", "wen", "cor", "dun", "hart", "raven", "ox"
      ),
      placeTails = listOf(
        "ford", "ton", "field", "bury", "wick", "combe", "mere", "hall", "worth", "stead",
        "bridge", "leigh", "grove", "marsh", "hollow"
      ),
      peopleTails = listOf("ings", "folk", "shire", "marches", "vale"),
      givenNames = listOf(
        male("alden"), male("bertrand"), male("cuthred"), female("edila"), male("godwin"), female("hilda"),
        male("leofric"), female("mildred"), male("osric"), female("rowena"), male("wulfric"), female("aelfwyn"),
        male("tobin"), female("maerit")
      ),
      epithets = listOf("green", "harvest", "elder", "long", "still")
    ),
    // Seafaring: Norse and Hanseatic coast.
    Style(
      placeStems = listOf(
        "salt", "storm", "gull", "kirk", "north", "west", "bran", "skag", "ver", "hel",
        "fjell", "drang", "orm", "sund", "vind", "gar", "hafn", "sig"
      ),
      placeTails = listOf(
        "holm", "havn", "vik", "ness", "strand", "fjord", "oy", "sund", "berg", "skar",
        "reef", "quay", "gate", "mouth"
      ),
      peopleTails = listOf("mennir", "folk", "kin", "sund", "reach"),
      givenNames = listOf(
        male("asgeir"), female("brynja"), male("dagfinn"), female("eir"), male("halvard"), female("ingrid"),
        male("jorund"), male("kettil"), female("ragna"), male("sigvard"), female("thorunn"), female("vigdis"),
        male("olav"), female("sunniva")
      ),
      epithets = listOf("storm", "salt", "winter", "tide", "gale")
    ),
    // Pastoral: steppe.
    Style(
      placeStems = listOf(
        "kara", "altan", "sary", "kizil", "tor", "buruk", "aral", "temer", "chagan", "orkho",
        "jelme", "sube", "ulan", "borte", "kesh"
      ),
      placeTails = listOf(
        "gol", "tau", "kum", "bulak", "kent", "obo", "ordu", "yurt", "dag", "steppe",
        "ford", "wells", "grass"
      ),
      peopleTails = listOf("khai", "ulus", "horde", "band", "riders"),
      // Many Mongolian names are given to either sex. Each is tagged as it is most often given.
      givenNames = listOf(
        female("altani"), male("batu"), male("chuluun"), male("erdene"), female("gerel"), female("khulan"),
        male("muunokhoi"), female("narantsetseg"), female("oyuun"), male("saikhan"), male("temujin"),
        female("tsetseg"), female("yesui"), male("bayan")
      ),
      epithets = listOf("sky", "wind", "far", "swift", "grass")
    ),
    // Highland: mining country, mostly Germanic and Cornish.
    Style(
      placeStems = listOf(
        "iron", "stan", "grim", "cold", "high", "deep", "drear", "kar", "hoch", "stein",
        "erz", "silber", "graf", "wolk", "tre", "pen", "bos"
      ),
      placeTails = listOf(
        "fell", "delve", "shaft", "crag", "hold", "gate", "burg", "tal", "stein", "grube",
        "scar", "pike", "tor", "vein"
      ),
      peopleTails = listOf("volk", "guild", "hold", "delvers", "clans"),
      givenNames = listOf(
        male("adalgar"), female("berta"), male("dietrich"), male("erwin"), male("gunther"), female("hedda"),
        male("kunimund"), female("liesel"), male("otbert"), female("reinhild"), male("sigmar"), female("ulrica"),
        male("warin"), male("trevanion")
      ),
      epithets = listOf("iron", "deep", "grim", "under", "hollow")
    )
  )

  /** Bynames by role, because "the Lawgiver" belongs to a ruler and "the Quenched" to a smith. */
  internal val BYNAMES: Map<FigureRole, List<String>> = mapOf(
    FigureRole.RULER to listOf(
      "the Elder", "the Lawgiver", "the Unquiet", "the Wide-Handed", "the Second",
      "the Reluctant", "the Grey", "the Cruel"
    ),
    FigureRole.GENERAL to listOf(
      "the Spear", "Ironhand", "the Unbeaten", "the Bloody", "the Watchful", "One-Eye",
      "the Bridge-Breaker"
    ),
    FigureRole.PROPHET to listOf(
      "the Voice", "the Barefoot", "the Burned", "the Sleepless", "the Weeper", "the Silent"
    ),
    FigureRole.SMITH to listOf(
      "the Quenched", "Blackthumb", "the Patient", "Nine-Fingers", "the Exacting", "Coalbeard"
    ),
    FigureRole.EXPLORER to listOf(
      "the Far-Walked", "the Lost", "Saltbitten", "the Returned", "the Mapmaker"
    ),
    FigureRole.SCHOLAR to listOf(
      "the Annalist", "the Doubter", "Inkhand", "the Tiresome", "the Counter"
    )
  )

  internal val ARTIFACT_NOUNS: Map<ArtifactKind, List<String>> = mapOf(
    ArtifactKind.BLADE to listOf("brand", "edge", "fang", "sabre", "cleaver", "spike"),
    ArtifactKind.CROWN to listOf("crown", "circlet", "diadem", "helm", "torc"),
    ArtifactKind.RELIQUARY to listOf("reliquary", "casket", "shrine", "urn", "coffer"),
    ArtifactKind.TOME to listOf("codex", "annal", "testament", "ledger", "scroll"),
    ArtifactKind.RING to listOf("ring", "band", "seal", "signet"),
    ArtifactKind.BANNER to listOf("banner", "standard", "pennon", "colours")
  )

  /**
   * Fingerprint of every word pool.
   *
   * Worth having for a reason the other catalogues do not share: this file's own KDoc says that **changing it
   * renames the entire world**, because a name is a seed plus these pools rather than a stored string. There is
   * no way to detect that from the outside - a renamed world looks like a working world - so the only defence
   * is that the pools reach a version number, which is what this is for.
   *
   * Folded in list order and by key name. Order is load bearing twice over: [STYLES] is indexed by the culture's
   * position in `Culture.ALL`, and `pick` selects within a pool by index, so moving one word along renames
   * everything that had been landing on it.
   */
  fun catalogueDigest(): Long {
    val digest = ParamsDigest()

    STYLES.forEachIndexed { index, style ->
      digest.nested(
        "style$index",
        ParamsDigest()
          .put("placeStems", style.placeStems.joinToString(","))
          .put("placeTails", style.placeTails.joinToString(","))
          .put("peopleTails", style.peopleTails.joinToString(","))
          .put("givenNames", style.givenNames.joinToString(",") { it.name })
          .put("givenNameSexes", style.givenNames.joinToString(",") { it.sex.name })
          .put("epithets", style.epithets.joinToString(","))
          .value
      )
    }
    for ((role, words) in BYNAMES) digest.put("byname:${role.name}", words.joinToString(","))
    for ((kind, words) in ARTIFACT_NOUNS) digest.put("noun:${kind.name}", words.joinToString(","))
    // Culture-independent and so outside STYLES, but named the same way and by the same `pick` - so they carry
    // exactly the property the KDoc above says has to reach a version number.
    digest.put("woundEpithets", WOUND_EPITHETS.joinToString(","))
    digest.put("woundNouns", WOUND_NOUNS.joinToString(","))
    for ((domain, titles) in DEITY_TITLES) digest.put("deity:${domain.name}", titles.joinToString(","))
    digest.put("cultBands", CULT_BANDS.joinToString(","))
    digest.put("monsterNames", MONSTER_NAMES.joinToString(","))
    for ((beast, noun) in BEAST_NOUNS) digest.put("beast:${beast.name}", noun)
    // How a site is phrased lives in code, not in a pool, so the phrases themselves are folded.
    val siteForms = SiteKind.entries.flatMap { kind -> (Faction.entries + null).map { siteForm(kind, it) } }.distinct()
    digest.put("siteNames", siteForms.joinToString(",") { site(0L, "%", it) })

    return digest.value
  }
}
