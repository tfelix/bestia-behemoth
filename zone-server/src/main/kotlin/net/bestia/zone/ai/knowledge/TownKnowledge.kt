package net.bestia.zone.ai.knowledge

import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.HistoryEvent
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pop.Household
import net.bestia.worldgen.pop.PopulationSummary
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.world.SettlementLoreService
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.pow

/**
 * Who in one settlement holds which of its memories.
 *
 * ### Two layers, and keeping them apart is the design
 *
 * **The town decides the pool.** What could be known here at all is a property of where the settlement
 * stands, what reached it and how famous it was - see [SettlementLoreService.knownAt]. No individual is
 * involved.
 *
 * **The town's people are assigned it.** How many hold a memory comes from the memory alone
 * ([shareOf]); *which* of them comes from a weighted draw ([keyOf]). A trade only ever reorders that
 * draw, and can neither put a memory into a town nor promise anybody one.
 *
 * That separation is the whole point, and the reason a reach multiplier per trade was rejected: a
 * multiplier is a total order, so one trade would be a wholesale replacement for every other and a
 * player would stop talking to anybody else. `TownKnowledgeTest` bounds how far one trade's share of the
 * town's news may sit above another's - a bound on the mean rather than a subset check, because a subset
 * check only measures how many households a trade happens to have.
 *
 * The consequence a player notices is at the bottom of the spread table, where exactly one household
 * holds a memory and finding them is the point. A weighted argmax rather than a coin flip per
 * household, deliberately: a binomial draw at *p = 1/n* would sometimes leave **nobody** holding a
 * thing, and a memory nobody can be told is a dead end indistinguishable from a bug.
 *
 * Nothing is stored. A reseed rebuilds it, which is the only correct answer - a settlement index means
 * nothing across two worlds.
 */
class TownKnowledge(
  /** Held by every household, so the common case costs no per-household storage at all. */
  private val universal: List<Knowledge>,
  private val byHousehold: Map<Int, List<Knowledge>>,
) {

  fun heldBy(household: Int): List<Knowledge> {
    val own = byHousehold[household] ?: return universal

    return universal + own
  }

  /** Every memory anybody here holds. For tooling and tests. */
  fun all(): List<Knowledge> {
    return universal + byHousehold.values.flatten().distinctBy { it.topic }
  }

  /** How many households hold each memory, by topic. Universal ones are absent. */
  fun holderCounts(): Map<Int, Int> {
    val counts = HashMap<Int, Int>()
    byHousehold.values.forEach { held -> held.forEach { counts.merge(it.topic, 1, Int::plus) } }

    return counts
  }

  /**
   * The memories exactly one household in the town holds, and which household.
   *
   * The property the whole model exists for, so it is worth being able to ask for it directly rather
   * than inferring it from a histogram.
   */
  fun soleHeld(): List<Pair<Int, Knowledge>> {
    val once = holderCounts().filterValues { it == 1 }.keys

    return byHousehold.entries
      .flatMap { (household, held) -> held.filter { it.topic in once }.map { household to it } }
  }

  /** What every household in the town holds without having to be anybody in particular. */
  fun universal(): List<Knowledge> {
    return universal
  }

  companion object {

    /**
     * Importance at or above which everybody knows it, wherever and whenever they are.
     *
     * `Households.FAMOUS_IMPORTANCE`, which is private to the generator. Restated rather than shared:
     * that one decides what a household plausibly heard and this one what a whole town holds in common.
     * They agree today and are allowed to diverge.
     */
    const val FAMOUS = 70

    /** A town's own event big enough that most of the town carries it: a founding, a sacking. */
    const val MAJOR = 60

    /** Enough to be worth repeating, for something that happened somewhere else. */
    const val NOTABLE = 50

    /**
     * Years past which nobody alive saw it and the memory is somebody's grandmother's.
     *
     * Longer than a working lifetime on purpose: an eighty-year-old heard it from somebody who was
     * there, and that is exactly who the bottom row of the spread table means to single out.
     */
    const val LIVING_MEMORY_YEARS = 80

    /** The oldest anybody gets, as the denominator for how much age is worth in the draw. */
    private const val OLDEST_AGE = 80.0

    private const val SHARE_OWN_MAJOR = 0.6
    private const val SHARE_OWN = 0.3
    private const val SHARE_NOTABLE = 0.15
    private const val SHARE_LESSER = 0.04

    /** One household and no more. A share, so the arithmetic stays uniform; the count is floored at one. */
    private const val SHARE_SOLE = 0.0

    /** How far one person's curiosity may swing off their trade's. */
    private const val JITTER_MIN = 0.5
    private const val JITTER_MAX = 2.0

    /** How much being the oldest household in the village is worth, for a memory nobody living saw. */
    private const val AGE_WEIGHT = 1.0

    private const val HOLDER_SALT = 0x4B4EL
    private const val JITTER_SALT = 0x4B4FL

    /**
     * @param householdAt the settlement's households by index, or null where there is nobody
     * @param profileOf the household's trade as a weighting. A function rather than the catalogues
     *   themselves, so every decision here stays pure and a test can build a town and assert on who
     *   knows what with no Spring context - the argument `SettlementLoreService` makes for its own
     *   companion.
     */
    fun of(
      generated: GeneratedWorld,
      settlement: Int,
      summary: PopulationSummary,
      worldSeed: Long,
      householdAt: (Int) -> Household?,
      profileOf: (Household) -> KnowledgeProfile,
      variantsOf: (EventKind) -> Int = { 1 },
      nearbyRange: Double = SettlementLoreService.NEARBY_RANGE,
      positions: Map<Int, Vec2d> = SettlementLoreService.settlementPositions(generated),
    ): TownKnowledge {
      val chronicle = generated.world.chronicle
      val candidates = poolOf(generated, settlement, nearbyRange, positions)

      val occupants = (0 until summary.householdCount).mapNotNull { index ->
        householdAt(index)?.let { Occupant(worldSeed, settlement, index, it, profileOf(it)) }
      }
      if (candidates.isEmpty() || occupants.isEmpty()) {
        return TownKnowledge(emptyList(), emptyMap())
      }

      val universal = ArrayList<Knowledge>()
      val byHousehold = HashMap<Int, MutableList<Knowledge>>()

      for (candidate in candidates) {
        val knowledge =
          HistoryKnowledge.of(chronicle, candidate.event, candidate.locality, variantsOf(candidate.event.kind))
        val share = shareOf(candidate, chronicle.presentYear)

        if (share >= 1.0) {
          universal.add(knowledge)
          continue
        }

        val yearsAgo = chronicle.presentYear - candidate.event.year
        // Floored at one, which is what turns SHARE_SOLE into a single holder and what stops a hamlet of
        // twelve losing its rarest memories to rounding.
        val wanted = ceil(share * occupants.size).toInt().coerceIn(1, occupants.size)

        occupants
          .sortedByDescending { keyOf(it, candidate.event, yearsAgo) }
          .take(wanted)
          .forEach { byHousehold.getOrPut(it.index) { ArrayList() }.add(knowledge) }
      }

      return TownKnowledge(universal, byHousehold)
    }

    /**
     * The settlement's own events, what it witnessed nearby, and what everybody everywhere knows.
     *
     * The first two come from the one query that already exists; the famous tier is added here because
     * how famous is famous enough is a property of the listener rather than of the town.
     */
    private fun poolOf(
      generated: GeneratedWorld,
      settlement: Int,
      nearbyRange: Double,
      positions: Map<Int, Vec2d>,
    ): List<Candidate> {
      val local = SettlementLoreService.knownAt(generated, settlement, nearbyRange, positions)
      val seen = local.mapTo(HashSet()) { it.event.id }

      val here = local.map { located ->
        val locality = when {
          located.event.importance >= FAMOUS -> Locality.FAMOUS
          located.own -> Locality.OWN_TOWN
          else -> Locality.NEARBY
        }

        Candidate(located.event, locality, located.own)
      }

      val famous = generated.world.chronicle.events
        .filter { it.importance >= FAMOUS && it.id !in seen }
        .map { Candidate(it, Locality.FAMOUS, own = false) }

      return here + famous
    }

    /**
     * What share of the town holds this, from the memory alone.
     *
     * The branch order is the table in the design document and is significant: "near, lesser and older
     * than anybody living" is narrower than "near, lesser", so it has to be asked first.
     */
    private fun shareOf(candidate: Candidate, presentYear: Int): Double {
      val event = candidate.event

      return when {
        event.importance >= FAMOUS -> 1.0
        candidate.own && event.importance >= MAJOR -> SHARE_OWN_MAJOR
        candidate.own -> SHARE_OWN
        event.importance >= NOTABLE -> SHARE_NOTABLE
        presentYear - event.year > LIVING_MEMORY_YEARS -> SHARE_SOLE
        else -> SHARE_LESSER
      }
    }

    /**
     * This household's place in the contest for one memory.
     *
     * `u^(1/w)` rather than `u * w`: it is the standard way to take a weighted sample without
     * replacement, so "three times the weight" means the same thing however many households are
     * competing, which a plain product does not.
     */
    private fun keyOf(occupant: Occupant, event: HistoryEvent, yearsAgo: Int): Double {
      val unit = GenRng.hashUnit(
        occupant.worldSeed,
        occupant.settlement.toLong(),
        event.id.toLong(),
        occupant.index.toLong(),
        HOLDER_SALT,
      )

      return unit.pow(1.0 / occupant.weightFor(event.kind, yearsAgo))
    }

    private class Candidate(val event: HistoryEvent, val locality: Locality, val own: Boolean)

    /**
     * One household in the contest for a memory.
     *
     * The per-person [jitter] is what stops a trade deciding the answer, and it is drawn from the
     * household rather than from the trade on purpose: this particular farmer is unusually
     * well-travelled, and that particular innkeeper is incurious.
     */
    private class Occupant(
      val worldSeed: Long,
      val settlement: Int,
      val index: Int,
      household: Household,
      private val profile: KnowledgeProfile,
    ) {

      private val oldest = household.members.maxOfOrNull { it.age } ?: 0

      private val jitter =
        JITTER_MIN + (JITTER_MAX - JITTER_MIN) *
          GenRng.hashUnit(worldSeed, settlement.toLong(), index.toLong(), JITTER_SALT)

      fun weightFor(kind: EventKind, yearsAgo: Int): Double {
        return profile.weightFor(kind) * ageAffinity(yearsAgo) * jitter
      }

      /**
       * How much this household's age is worth against a memory of that age.
       *
       * Nothing at all for something that happened last year - everybody was there - rising to double
       * for the oldest household in the village against something nobody living saw.
       */
      private fun ageAffinity(yearsAgo: Int): Double {
        val agedness = min(1.0, yearsAgo / LIVING_MEMORY_YEARS.toDouble())

        return 1.0 + AGE_WEIGHT * (oldest / OLDEST_AGE) * agedness
      }
    }
  }
}
