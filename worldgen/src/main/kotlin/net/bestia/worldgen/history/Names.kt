package net.bestia.worldgen.history

import net.bestia.worldgen.core.ArtifactKind
import net.bestia.worldgen.core.FigureRole
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.ParamsDigest

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

  /** A personal name, plus a byname often enough that a chronicle does not read as a list of nouns. */
  fun person(seed: Long, cultureIndex: Int, role: FigureRole): String {
    val style = styleOf(cultureIndex)
    val given = pick(style.givenNames, seed, 3).replaceFirstChar { it.uppercase() }

    return if (unit(seed, 4) < 0.55) {
      "$given ${pick(BYNAMES[role] ?: BYNAMES.getValue(FigureRole.RULER), seed, 5)}"
    } else {
      given
    }
  }

  /** A civilisation's name: a people, not a place, so it takes the demonym form. */
  fun civ(seed: Long, cultureIndex: Int): String {
    val style = styleOf(cultureIndex)
    return (pick(style.placeStems, seed, 6) + pick(style.peopleTails, seed, 7))
      .replaceFirstChar { it.uppercase() }
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
  fun artifact(seed: Long, cultureIndex: Int, kind: ArtifactKind, ofPlaceSeed: Long): String {
    val noun = pick(ARTIFACT_NOUNS.getValue(kind), seed, 8)

    return if (ofPlaceSeed != 0L && unit(seed, 9) < 0.5) {
      "the ${noun.replaceFirstChar { it.uppercase() }} of ${place(ofPlaceSeed, cultureIndex)}"
    } else {
      val epithet = pick(styleOf(cultureIndex).epithets, seed, 10)
      "$epithet$noun".replaceFirstChar { it.uppercase() }
    }
  }

  /** A site name, which is nearly always possessive: "the barrow of X", "Y field". */
  fun site(seed: Long, cultureIndex: Int, of: String, form: String): String = when (form) {
    "barrow" -> "the barrow of $of"
    "field" -> "$of Field"
    "ruin" -> "the ruins of $of"
    else -> "the $form of $of"
  }

  private fun styleOf(cultureIndex: Int): Style = STYLES[cultureIndex.coerceIn(0, STYLES.size - 1)]

  private fun pick(pool: List<String>, seed: Long, salt: Long): String =
    pool[(GenRng.hash(seed, salt) ushr 1).mod(pool.size.toLong()).toInt()]

  private fun unit(seed: Long, salt: Long): Double = GenRng.hashUnit(seed, salt)

  /** The word pools one culture draws on. */
  private class Style(
    val placeStems: List<String>,
    val placeTails: List<String>,
    val peopleTails: List<String>,
    val givenNames: List<String>,
    val epithets: List<String>
  )

  /**
   * One pool set per [net.bestia.worldgen.civ.Culture], in the same order as `Culture.ALL`.
   *
   * Indexed by culture rather than looked up by name so that adding a culture is a compile-time hole here
   * - `STYLES[index]` would silently reuse the last style otherwise, and every new culture would be named
   * like a highland one.
   */
  private val STYLES = listOf(
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
        "alden", "bertrand", "cuthred", "edila", "godwin", "hilda", "leofric", "mildred",
        "osric", "rowena", "wulfric", "aelfwyn", "tobin", "maerit"
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
        "asgeir", "brynja", "dagfinn", "eir", "halvard", "ingrid", "jorund", "kettil",
        "ragna", "sigvard", "thorunn", "vigdis", "olav", "sunniva"
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
      givenNames = listOf(
        "altani", "batu", "chuluun", "erdene", "gerel", "khulan", "muunokhoi", "narantsetseg",
        "oyuun", "saikhan", "temujin", "tsetseg", "yesui", "bayan"
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
        "adalgar", "berta", "dietrich", "erwin", "gunther", "hedda", "kunimund", "liesel",
        "otbert", "reinhild", "sigmar", "ulrica", "warin", "trevanion"
      ),
      epithets = listOf("iron", "deep", "grim", "under", "hollow")
    )
  )

  /** Bynames by role, because "the Lawgiver" belongs to a ruler and "the Quenched" to a smith. */
  private val BYNAMES: Map<FigureRole, List<String>> = mapOf(
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

  private val ARTIFACT_NOUNS: Map<ArtifactKind, List<String>> = mapOf(
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
          .put("givenNames", style.givenNames.joinToString(","))
          .put("epithets", style.epithets.joinToString(","))
          .value
      )
    }
    for ((role, words) in BYNAMES) digest.put("byname:${role.name}", words.joinToString(","))
    for ((kind, words) in ARTIFACT_NOUNS) digest.put("noun:${kind.name}", words.joinToString(","))

    return digest.value
  }
}
