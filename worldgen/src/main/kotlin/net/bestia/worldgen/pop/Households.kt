package net.bestia.worldgen.pop

import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.HistoryEvent
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.max
import kotlin.math.min

/** What a household member is to the head of it. */
enum class Kinship { HEAD, SPOUSE, CHILD, APPRENTICE, SERVANT, ELDER }

/** One person, at the level of detail a distant settlement is stored at: none. */
class Member(val age: Int, val kinship: Kinship)

/**
 * One household: a dwelling, an occupation, a wealth, and the people in it.
 *
 * Note what is absent: names, a position, an entity id, any state. This is the *expansion* of a seed, not a
 * stored record - see [Households].
 */
class Household(
  val index: Int,
  /** Index into [BusinessCatalogue.ALL], or -1 for a farming household. */
  val business: Int,
  val sector: Sector,
  val wealth: Double,
  val members: List<Member>
) {
  val size get() = members.size

  val head: Member get() = members.first()
}

/**
 * Everything stored about a settlement's people, which is a summary and a seed.
 *
 * This is the whole of the architecture document's agent LOD, and the reason it is only a summary: a world
 * of three hundred settlements and four hundred thousand people cannot store households, and it does not
 * have to. `{population, wealth, businesses, seed}` is a few dozen bytes; [Households.expand] turns it back
 * into the households deterministically whenever a player is near enough to need them, and they collapse
 * back to nothing when the player leaves.
 */
class PopulationSummary(
  val settlement: Int,
  val position: Vec2d,
  val population: Int,
  val wealth: Double,
  val householdCount: Int,
  val seed: Long,
  /** The roster: `(index into BusinessCatalogue.ALL, count)`, in catalogue order. */
  val businesses: List<Pair<Int, Int>>,
  /** People by [Sector], indexed by ordinal. */
  val sectors: IntArray
)

/**
 * Turns a [PopulationSummary] back into households.
 *
 * ### Why this is a function and not a stage
 *
 * A stage produces something the world tier stores. This produces something the world tier deliberately does
 * *not* store, on demand, from eight bytes and a handful of counts - which is what makes a distant town cost
 * nothing and a nearby one cost what it is worth. It is a pure function of the summary, so a household
 * expanded on the zone server and the same household expanded in a tool are the same household.
 *
 * ### Determinism, again
 *
 * Every draw is `hash(seed, household, salt)`. No stream, so household 400 is the same household whether or
 * not households 0 to 399 were expanded first - which is the property that lets a caller expand *one*
 * household, for the one building a player walked into, rather than the whole town.
 */
object Households {

  /** Every household of a settlement, in index order. */
  fun expand(summary: PopulationSummary): List<Household> =
    (0 until summary.householdCount).map { one(summary, it) }

  /**
   * One household, without expanding any of the others.
   *
   * The occupation comes from the roster by *position*: businesses are laid out in catalogue order, each
   * taking as many households as it employs, and the remainder farm. So household 0 is always the first
   * business of the first trade the settlement has, which makes the mapping stable under a growing town -
   * adding people adds farmers at the end rather than renumbering everyone.
   */
  fun one(summary: PopulationSummary, index: Int): Household {
    require(index in 0 until summary.householdCount) {
      "Household $index is outside settlement ${summary.settlement}'s ${summary.householdCount}"
    }

    var remaining = index
    var business = -1
    var sector = Sector.FARM

    for ((type, count) in summary.businesses) {
      val households = max(1, count)
      if (remaining < households) {
        business = type
        sector = BusinessCatalogue.ALL[type].sector
        break
      }
      remaining -= households
    }

    val wealth = wealthOf(summary, index, sector)
    return Household(
      index = index,
      business = business,
      sector = sector,
      wealth = wealth,
      members = membersOf(summary, index, wealth, sector)
    )
  }

  /**
   * Household wealth: the settlement's, tilted by sector and spread.
   *
   * Log-ish rather than uniform, because wealth is: most households are near the bottom and a few are far
   * above it, and a uniform spread produces a town of comfortable equals - which reads as generated more
   * obviously than almost anything else here.
   */
  private fun wealthOf(summary: PopulationSummary, index: Int, sector: Sector): Double {
    val bias = when (sector) {
      Sector.TRADE -> 1.5
      Sector.ADMIN -> 1.35
      Sector.CLERGY -> 1.2
      Sector.CRAFT -> 1.0
      Sector.SERVICE -> 0.9
      Sector.MILITARY -> 0.85
      Sector.FARM -> 0.7
    }
    val roll = roll(summary.seed, index.toLong(),WEALTH_SALT)
    return (summary.wealth * bias * (0.35 + roll * roll * 1.9)).coerceIn(0.02, 1.0)
  }

  /**
   * The people in a household, with ages from a plausible pre-industrial pyramid.
   *
   * Pre-industrial means broad at the base and thin at the top: about a third of the population under
   * fifteen and very few over sixty. Drawing ages uniformly instead produces a town with no children in it,
   * which is the sort of thing nobody notices until an NPC schedule sends a four-year-old to work.
   */
  private fun membersOf(
    summary: PopulationSummary,
    index: Int,
    wealth: Double,
    sector: Sector
  ): List<Member> {
    val out = ArrayList<Member>(6)

    val headAge = 24 + (roll(summary.seed, index.toLong(),HEAD_AGE_SALT) * 34).toInt()
    out.add(Member(headAge, Kinship.HEAD))

    if (roll(summary.seed, index.toLong(),SPOUSE_SALT) < SPOUSE_CHANCE) {
      out.add(Member(headAge + (roll(summary.seed, index.toLong(),SPOUSE_AGE_SALT) * 9 - 4).toInt(), Kinship.SPOUSE))
    }

    // Children: as many as the head is old enough to have, capped, and fewer in a poor household because
    // fewer survived.
    val fertile = ((headAge - 22) / 4).coerceIn(0, MAX_CHILDREN)
    val children = (fertile * (0.4 + wealth * 0.5) +
        roll(summary.seed, index.toLong(),CHILDREN_SALT) * 1.5).toInt().coerceIn(0, MAX_CHILDREN)
    for (c in 0 until children) {
      val age = (roll(summary.seed, index * 8L + c, CHILD_AGE_SALT) * min(17, headAge - 20)).toInt()
      out.add(Member(max(0, age), Kinship.CHILD))
    }

    // An apprentice belongs to a trade, not to a farm; a servant belongs to money.
    if (sector == Sector.CRAFT && roll(summary.seed, index.toLong(),APPRENTICE_SALT) < APPRENTICE_CHANCE) {
      out.add(Member(13 + (roll(summary.seed, index.toLong(),APPRENTICE_AGE_SALT) * 7).toInt(), Kinship.APPRENTICE))
    }
    if (wealth > SERVANT_WEALTH && roll(summary.seed, index.toLong(),SERVANT_SALT) < SERVANT_CHANCE) {
      out.add(Member(16 + (roll(summary.seed, index.toLong(),SERVANT_AGE_SALT) * 30).toInt(), Kinship.SERVANT))
    }
    if (roll(summary.seed, index.toLong(),ELDER_SALT) < ELDER_CHANCE) {
      out.add(Member(58 + (roll(summary.seed, index.toLong(),ELDER_AGE_SALT) * 20).toInt(), Kinship.ELDER))
    }

    return out
  }

  /**
   * A sparse small-world social graph over a settlement's households.
   *
   * A ring lattice with a fraction of its edges rewired long-range - Watts-Strogatz, as the architecture
   * document asks for. The shape matters for one reason: gossip. A pure lattice propagates news at walking
   * pace round a circle and a random graph propagates it instantly; a small-world graph propagates it the
   * way a town does, mostly to neighbours with the occasional jump across town, so a rumour reaches the far
   * side in a few hops without everybody knowing at once.
   *
   * @return neighbours per household, sorted, without self-edges
   */
  fun socialGraph(
    summary: PopulationSummary,
    neighbours: Int = LATTICE_DEGREE,
    rewireChance: Double = REWIRE_CHANCE
  ): List<IntArray> {
    val n = summary.householdCount
    if (n <= 1) return List(n) { IntArray(0) }

    val sets = List(n) { LinkedHashSet<Int>() }
    val half = max(1, neighbours / 2)

    for (a in 0 until n) {
      for (step in 1..half) {
        var b = (a + step) % n
        if (roll(summary.seed, a * 64L + step, REWIRE_SALT) < rewireChance) {
          val jump = (roll(summary.seed, a * 64L + step, JUMP_SALT) * n).toInt().coerceIn(0, n - 1)
          if (jump != a) b = jump
        }
        if (b == a) continue
        sets[a].add(b)
        sets[b].add(a)
      }
    }

    return sets.map { it.sorted().toIntArray() }
  }

  /**
   * What a household is likely to know: the events it lived through, near it, plus what everyone knows.
   *
   * Three filters, and the third is the one that makes the result feel right. Within a lifetime, because
   * nobody remembers what happened before they were born except as a story; within range, because news
   * travelled at walking pace; and *regardless of either* if the event was important enough, because the
   * founding of the kingdom is known in every village whether or not anyone there saw it.
   *
   * This is the seed of the knowledge/rumour model rather than the model itself: it says what a household
   * plausibly heard, and does not track confidence, distortion, or who told whom.
   */
  fun knowledgeOf(
    summary: PopulationSummary,
    household: Household,
    chronicle: Chronicle,
    /** Metres within which local news reaches this settlement. */
    range: Double = NEWS_RANGE,
    /** Importance at or above which an event is known everywhere. */
    famous: Int = FAMOUS_IMPORTANCE
  ): List<HistoryEvent> {
    val oldest = household.members.maxOfOrNull { it.age } ?: 0
    val bornIn = chronicle.presentYear - oldest

    return chronicle.events.filter { event ->
      val remembered = event.year >= bornIn - INHERITED_MEMORY_YEARS
      val near = event.where?.let { it.distanceTo(summary.position) <= range } ?: false
      (remembered && near) || event.importance >= famous
    }
  }

  private fun roll(seed: Long, key: Long, salt: Long): Double = GenRng.hashUnit(seed, key, salt)

  private const val SPOUSE_CHANCE = 0.78
  private const val APPRENTICE_CHANCE = 0.35
  private const val SERVANT_CHANCE = 0.5
  private const val SERVANT_WEALTH = 0.55
  private const val ELDER_CHANCE = 0.22
  private const val MAX_CHILDREN = 5

  /** Metres local news carries. About a day's walk each way, which is how far a market draws from. */
  private const val NEWS_RANGE = 25_000.0

  /** Importance at or above which an event is common knowledge everywhere. */
  private const val FAMOUS_IMPORTANCE = 70

  /** Years before a household's oldest member's birth that still reach them, as their parents' stories. */
  private const val INHERITED_MEMORY_YEARS = 40

  private const val LATTICE_DEGREE = 6
  private const val REWIRE_CHANCE = 0.12

  private const val WEALTH_SALT = 0x51L
  private const val HEAD_AGE_SALT = 0x52L
  private const val SPOUSE_SALT = 0x53L
  private const val SPOUSE_AGE_SALT = 0x54L
  private const val CHILDREN_SALT = 0x55L
  private const val CHILD_AGE_SALT = 0x56L
  private const val APPRENTICE_SALT = 0x57L
  private const val APPRENTICE_AGE_SALT = 0x58L
  private const val SERVANT_SALT = 0x59L
  private const val SERVANT_AGE_SALT = 0x5AL
  private const val ELDER_SALT = 0x5BL
  private const val ELDER_AGE_SALT = 0x5CL
  private const val REWIRE_SALT = 0x5DL
  private const val JUMP_SALT = 0x5EL
}
