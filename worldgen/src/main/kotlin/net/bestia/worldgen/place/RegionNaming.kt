package net.bestia.worldgen.place

import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.World
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d

/**
 * Turns a partition into names, and ties them to what happened on the ground.
 *
 * ### Uniqueness is local, not global
 *
 * `civ/Districts.distinctNameSeed` re-rolls a quarter's name against the *other quarters of its own
 * town*, not against the world, and that scope is the right one here too - for a harder reason than
 * taste. A culture has about twenty place stems and there are ten region forms, so regions competing for
 * one form in one culture are drawing from twenty names. A hundred and eighty regions cannot all differ,
 * and no number of re-rolls changes that.
 *
 * Two identical names on opposite sides of an ocean are how real toponymy works. Two touching each other
 * are a genuine problem, because that is the pair a player would be trying to tell apart. So the
 * constraint is: **no region shares a name with a region it borders**, and beyond that duplicates are
 * accepted rather than engineered away.
 *
 * ### History gets first refusal
 *
 * A region named for something that happened in it beats one named for what grows there, so the sources
 * are tried in order of how much they say. What makes this nearly free is that `Names.site`'s
 * `else -> "the $form of $of"` branch already handles an arbitrary form word, so a region's own form
 * flows straight into it - "the downs of Ashford" needed no addition to `Names` at all.
 */
object RegionNaming {

  /** A region's name and the two facts that produced it. */
  class Named(
    val name: String,
    val nameSeed: Long,
    /** Index into `Culture.ALL`, or -1 where nothing is near enough to have named the place. */
    val cultureIndex: Int
  )

  /**
   * Names every region.
   *
   * @param neighbours per region, the regions it shares a border with. Only used to scope uniqueness.
   * @param regionOf which region owns a world position, in metres.
   */
  fun nameAll(
    world: World,
    count: Int,
    kinds: List<RegionKind>,
    neighbours: List<IntArray>,
    regionOf: (Double, Double) -> Int
  ): List<Named> {
    val chronicle = world.chronicle
    val worldSeed = world.config.seed

    val settlements = readSettlements(world, count, regionOf)
    val heritage = readHeritage(chronicle, settlements, count, regionOf)

    val chosen = arrayOfNulls<String>(count)

    return (0 until count).map { region ->
      val seed = Names.seedOf(worldSeed, NAME_KEY, region.toLong())
      val form = kinds[region].form
      val culture = settlements.culture[region]
      val taken = HashSet<String>()
      for (neighbour in neighbours[region]) chosen[neighbour]?.let { taken.add(it) }

      val name = heritage[region]?.render(form)?.takeIf { it !in taken }
        ?: descriptive(seed, culture, form, taken)

      chosen[region] = name
      Named(name, seed, culture)
    }
  }

  /**
   * A stem-and-form name that none of the region's neighbours already carries.
   *
   * Re-rolls the seed first, then widens the pool with an epithet, then gives up and accepts a
   * duplicate. Giving up is correct rather than lazy: `history/Names.kt` states that names are only ever
   * displayed and never compared, so a repeat is cosmetic - and looping until unique on a world with more
   * regions than names would not terminate.
   */
  private fun descriptive(seed: Long, culture: Int, form: String, taken: Set<String>): String {
    for (attempt in 0 until ATTEMPTS) {
      val rolled = if (attempt == 0) seed else Names.seedOf(seed, attempt.toLong())
      val plain = Names.region(rolled, culture, form)
      if (plain !in taken) return plain
    }

    for (attempt in 0 until ATTEMPTS) {
      val rolled = Names.seedOf(seed, EPITHET_KEY, attempt.toLong())
      val widened = Names.region(rolled, culture, form, epithet = true)
      if (widened !in taken) return widened
    }

    return Names.region(seed, culture, form, epithet = true)
  }

  /**
   * Which culture named each region, and where each settlement is.
   *
   * The best-tier settlement in the region wins, not the nearest to its centre: a city and a hamlet in
   * one valley is a valley named by the city.
   */
  private fun readSettlements(
    world: World,
    count: Int,
    regionOf: (Double, Double) -> Int
  ): Settlements {
    val culture = IntArray(count) { -1 }
    val bestTier = IntArray(count) { Int.MAX_VALUE }
    val cultureOfSettlement = HashMap<Int, Int>()
    val positionOfSettlement = HashMap<Int, Vec2d>()

    for (feature in world.features.all()) {
      if (feature !is PointMarker || feature.kind != FeatureKind.SETTLEMENT) continue

      val index = feature.attribute(SettlementChannels.INDEX).toInt()
      val tier = feature.attribute(SettlementChannels.TIER).toInt()
      val settlementCulture = feature.attribute(SettlementChannels.CULTURE).toInt()

      cultureOfSettlement[index] = settlementCulture
      positionOfSettlement[index] = feature.position

      val region = regionOf(feature.position.x, feature.position.y)
      if (tier < bestTier[region]) {
        bestTier[region] = tier
        culture[region] = settlementCulture
      }
    }

    return Settlements(culture, cultureOfSettlement, positionOfSettlement)
  }

  /**
   * The most telling thing history left in each region, if anything.
   *
   * Ranked rather than scored, and the ranking is the whole judgement: a wound is the most distinctive
   * thing that can happen to a place, a razed town is the next, and being somebody's heartland is a fact
   * about people rather than about ground.
   */
  private fun readHeritage(
    chronicle: Chronicle,
    settlements: Settlements,
    count: Int,
    regionOf: (Double, Double) -> Int
  ): Array<Heritage?> {
    val heritage = arrayOfNulls<Heritage>(count)

    for (site in chronicle.sites) {
      val rank = when (site.kind) {
        SiteKind.WOUND -> WOUND
        SiteKind.RUIN, SiteKind.ASH_RUIN, SiteKind.BATTLEFIELD -> RESIDUE
        else -> continue
      }

      val region = regionOf(site.position.x, site.position.y)
      if (rank >= (heritage[region]?.rank ?: Int.MAX_VALUE)) continue

      if (rank == WOUND) {
        // `Names.site`'s wound branch ignores `of` entirely and names it off its own pools, because a
        // wound belongs to nobody. So this is already a whole name and no form is applied to it.
        heritage[region] = Heritage(rank, whole = Names.site(site.nameSeed, "", "wound"))
        continue
      }

      val record = chronicle.settlements.getOrNull(site.settlement) ?: continue
      if (record.nameSeed == 0L) continue

      val culture = settlements.cultureOfSettlement[site.settlement]
        ?: settlements.culture[region]
      heritage[region] = Heritage(
        rank = rank,
        seed = site.nameSeed,
        of = Names.place(record.nameSeed, culture.coerceAtLeast(0))
      )
    }

    for (civ in chronicle.civs) {
      val capital = settlements.positionOfSettlement[civ.capital] ?: continue
      val region = regionOf(capital.x, capital.y)
      if (HEARTLAND >= (heritage[region]?.rank ?: Int.MAX_VALUE)) continue

      heritage[region] = Heritage(
        rank = HEARTLAND,
        seed = civ.nameSeed,
        of = Names.civ(civ.nameSeed, civ.cultureIndex)
      )
    }

    return heritage
  }

  /** Where a region's name can come from, and how to render it once the form word is known. */
  private class Heritage(
    val rank: Int,
    val seed: Long = 0L,
    /** A possessive base - a town or a people - that the region's form attaches to. */
    val of: String? = null,
    /** A finished name that owes nothing to the region's form. */
    val whole: String? = null
  ) {

    fun render(form: String): String? {
      if (whole != null) return whole
      if (of == null) return null
      return Names.site(seed, of, form)
    }
  }

  private class Settlements(
    val culture: IntArray,
    val cultureOfSettlement: Map<Int, Int>,
    val positionOfSettlement: Map<Int, Vec2d>
  )

  private const val WOUND = 0
  private const val RESIDUE = 1
  private const val HEARTLAND = 2

  /** Salt separating a region's name seed from every other seed derived off the world seed. */
  private const val NAME_KEY = 0x5265676FL

  private const val EPITHET_KEY = 0x4570L

  /** `Districts.NAME_ATTEMPTS` is 48 for the same job at town scale. */
  private const val ATTEMPTS = 48
}
