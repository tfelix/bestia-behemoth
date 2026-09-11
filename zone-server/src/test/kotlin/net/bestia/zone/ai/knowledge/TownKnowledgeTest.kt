package net.bestia.zone.ai.knowledge

import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pop.EconomyProbe
import net.bestia.worldgen.pop.Household
import net.bestia.worldgen.pop.Households
import net.bestia.worldgen.pop.PopulationSummary
import net.bestia.worldgen.pop.Sector
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.world.SettlementLoreService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That knowledge is shared out the way the design says, on real worlds.
 *
 * Run against [TownKnowledge.of] rather than through Spring, which is the reason that entry point takes
 * functions instead of the catalogues: everything here is a pure function of a generated world, and a
 * test that had to stand up the container to check a lookup is a test nobody runs.
 *
 * ### Why it is a sweep
 *
 * Whether a town has a memory nobody living saw depends on where the generator put it and what happened
 * near it over eleven hundred years, and both are rolls. On any one seed the honest answer can be
 * nothing, so a per-seed assertion would either flake or be pinned to a lucky seed - and neither tells a
 * rare thing working apart from a dead one. `SettlementLoreTest` makes the same argument one tier down.
 */
class TownKnowledgeTest {

  /**
   * The property the whole model exists for.
   *
   * A reach multiplier per trade would make one occupation a wholesale replacement for another, a player
   * would work that out inside one village, and the rest of the town would become scenery. Stated as a
   * bound on the *mean* rather than as a subset check, because a subset check only measures how many
   * households a trade happens to have - a village with forty farmers and two priests will always have
   * the farmers holding more between them, and that is arithmetic rather than a gradient.
   *
   * Only contested memories count. The famous tier is known to everybody by construction and would
   * flatten any comparison it was included in.
   */
  @Test
  fun `no trade is a wholesale replacement for another`() {
    var townsChecked = 0
    var worst = 1.0
    var worstAt = ""

    for (seed in SEEDS) {
      val world = build(seed)

      forEachInhabitedTown(world) { settlement, summary ->
        if (summary.householdCount < MEANINGFUL_TOWN) return@forEachInhabitedTown

        val town = knowledgeOf(world, settlement, summary)
        val means = contestedMeansByTrade(summary, town)
        if (means.size < 2) return@forEachInhabitedTown

        townsChecked++
        val most = means.maxBy { it.value }
        val least = means.filterValues { it > 0.0 }.minByOrNull { it.value } ?: return@forEachInhabitedTown

        val ratio = most.value / least.value
        if (ratio > worst) {
          worst = ratio
          worstAt = "seed $seed town $settlement: '${most.key}' holds ${most.value} to " +
            "'${least.key}'\u0027s ${least.value}"
        }
      }
    }

    assertTrue(townsChecked > 0, "no town on any world had two trades to compare")
    println("checked $townsChecked towns, widest gap between trades ${"%.2f".format(worst)}x - $worstAt")

    assertTrue(
      worst <= MAX_TRADE_GAP,
      "one trade held ${"%.2f".format(worst)}x another trade's share of its town's news, over the " +
        "${MAX_TRADE_GAP}x this model allows. $worstAt. That is a reach gradient, and a player will find it."
    )
  }

  /**
   * That asking one well-connected person is not a substitute for asking the village.
   *
   * A rate rather than an absolute, and deliberately: in a small town with few contested memories,
   * somebody holding all of them is ordinary rather than a failure. What must not happen is that it is
   * the *common* case, because then every village has a shortcut.
   */
  @Test
  fun `hardly anybody holds the whole town's news`() {
    var towns = 0
    var withAKnowAll = 0

    for (seed in SEEDS) {
      val world = build(seed)

      forEachInhabitedTown(world) { settlement, summary ->
        val town = knowledgeOf(world, settlement, summary)
        val contested = town.all().map { it.topic }.toSet() - town.universal().map { it.topic }.toSet()
        if (contested.size < RICH_TOWN) return@forEachInhabitedTown

        towns++
        val knowAll = (0 until summary.householdCount).any { household ->
          town.heldBy(household).mapTo(HashSet()) { it.topic }.containsAll(contested)
        }
        if (knowAll) withAKnowAll++
      }
    }

    assertTrue(towns > 0, "no town anywhere had enough contested memories to ask")
    val rate = withAKnowAll.toDouble() / towns
    println("$withAKnowAll of $towns towns have somebody who knows all of it (${"%.1f".format(rate * 100)}%)")

    assertTrue(
      rate <= MAX_KNOW_ALL_RATE,
      "$withAKnowAll of $towns towns have one household holding every contested memory, which is " +
        "${"%.1f".format(rate * 100)}% of them. Asking around is supposed to be the point."
    )
  }

  /** Somebody has to hold the rarest memories, or the bottom of the spread table is a dead end. */
  @Test
  fun `every memory a town has is held by somebody`() {
    var checked = 0

    for (seed in SEEDS) {
      val world = build(seed)

      forEachInhabitedTown(world) { settlement, summary ->
        val town = knowledgeOf(world, settlement, summary)
        val everyone = (0 until summary.householdCount).flatMap { town.heldBy(it) }.map { it.topic }.toSet()

        for (memory in town.all()) {
          checked++
          assertTrue(
            memory.topic in everyone,
            "seed $seed town $settlement holds ${memory.key} and nobody in it does"
          )
        }
      }
    }

    assertTrue(checked > 0, "no town on any world held anything")
  }

  /** Two runs of the same world must produce the same villagers, or nothing downstream can be derived. */
  @Test
  fun `the same world hands out the same memories twice`() {
    val world = build(SEEDS.first())
    var compared = 0

    forEachInhabitedTown(world) { settlement, summary ->
      val first = knowledgeOf(world, settlement, summary)
      val second = knowledgeOf(world, settlement, summary)

      for (household in 0 until summary.householdCount) {
        assertEquals(
          first.heldBy(household).map { it.topic },
          second.heldBy(household).map { it.topic },
          "household $household of settlement $settlement was handed different memories on a second build"
        )
        compared++
      }
    }

    assertTrue(compared > 0, "no household to compare")
  }

  /**
   * That the rarest tier really is rare, and the commonest really is common.
   *
   * Asserted across the sweep rather than per town, because a hamlet of four households cannot
   * distinguish "one person" from "most of them" and should not be asked to.
   */
  @Test
  fun `a famous event is known to everybody and an old quiet one to hardly anyone`() {
    var universal = 0
    var sole = 0
    var townsChecked = 0

    for (seed in SEEDS) {
      val world = build(seed)

      forEachInhabitedTown(world) { settlement, summary ->
        if (summary.householdCount < MEANINGFUL_TOWN) return@forEachInhabitedTown
        townsChecked++

        val town = knowledgeOf(world, settlement, summary)
        universal += town.universal().size
        sole += town.soleHeld().size

        for (memory in town.universal()) {
          assertTrue(
            memory.importance >= TownKnowledge.FAMOUS,
            "seed $seed town $settlement tells everybody about ${memory.key} at importance ${memory.importance}"
          )
        }
      }
    }

    assertTrue(townsChecked > 0, "no town big enough to tell the tiers apart")
    println("across $townsChecked towns: $universal memories known to all, $sole held by one household")

    assertTrue(universal > 0, "no town anywhere had a memory everybody holds; the famous tier is dead")
    assertTrue(sole > 0, "no memory anywhere was held by exactly one household; the rarest tier is dead")
  }

  /**
   * That an interest is worth something, measured inside one town.
   *
   * The comparison has to be between households of the *same* town under *different* profiles. An
   * earlier version gave every household the same profile and compared two towns, which measures
   * nothing at all: the ranking is `u^(1/w)`, so a weight every household shares is a monotonic
   * transform of `u` and leaves the winners exactly as they were. It printed two identical numbers and
   * passed.
   *
   * Aggregated over the sweep, because in any one town the war memories may all be famous, or absent.
   */
  @Test
  fun `a trade that cares about war holds more of its town's wars`() {
    val watched = setOf(EventKind.BATTLE, EventKind.SIEGE, EventKind.SETTLEMENT_SACKED)
    var interested = 0.0
    var ordinary = 0.0
    var towns = 0

    for (seed in SEEDS) {
      val world = build(seed)

      forEachInhabitedTown(world) { settlement, summary ->
        if (summary.householdCount < MEANINGFUL_TOWN) return@forEachInhabitedTown

        // Half the town cares about war and half does not, so the profile is the only thing separating
        // the two groups - not where they live, not how many of them there are.
        val town = TownKnowledge.of(
          generated = world,
          settlement = settlement,
          summary = summary,
          worldSeed = WORLD_SEED,
          householdAt = { Households.one(summary, it) },
          profileOf = { if (caresAboutWar(it)) KnowledgeProfile(interests = watched) else KnowledgeProfile.ORDINARY },
          positions = positionsOf(world),
        )

        val universal = town.universal().mapTo(HashSet()) { it.topic }
        val names = watched.mapTo(HashSet()) { HistoryKnowledge.KEY_PREFIX + it.name }
        val topics = town.all().filter { it.key in names && it.topic !in universal }.mapTo(HashSet()) { it.topic }
        if (topics.isEmpty()) return@forEachInhabitedTown

        towns++
        interested += meanHolding(summary, town, topics) { caresAboutWar(it) }
        ordinary += meanHolding(summary, town, topics) { !caresAboutWar(it) }
      }
    }

    assertTrue(towns > 0, "no town anywhere had a contested war memory to weight")
    println("contested war memories per household over $towns towns: interested $interested, ordinary $ordinary")

    assertTrue(
      interested > ordinary,
      "weighting half a town toward war gave them $interested of its wars against the other half's " +
        "$ordinary. An interest that changes nothing is a catalogue nobody should be asked to fill in."
    )
  }

  /** Households of one group, and how many of [topics] the average one of them holds. */
  private fun meanHolding(
    summary: PopulationSummary,
    town: TownKnowledge,
    topics: Set<Int>,
    group: (Household) -> Boolean,
  ): Double {
    val members = (0 until summary.householdCount).filter { group(Households.one(summary, it)) }
    if (members.isEmpty()) return 0.0

    return members.sumOf { household -> town.heldBy(household).count { it.topic in topics } } / members.size.toDouble()
  }

  private fun caresAboutWar(household: Household): Boolean {
    return household.index % 2 == 0
  }

  /**
   * The mean number of contested memories a household of each trade holds.
   *
   * A mean rather than a union, so a trade with forty households is not automatically ahead of one with
   * two. Trades with too few households to average are dropped - one priest is a sample of one, and his
   * jitter alone would decide the answer.
   */
  private fun contestedMeansByTrade(summary: PopulationSummary, town: TownKnowledge): Map<String, Double> {
    val universal = town.universal().mapTo(HashSet()) { it.topic }
    val counts = HashMap<String, MutableList<Int>>()

    for (household in 0 until summary.householdCount) {
      val trade = tradeOf(Households.one(summary, household))
      val held = town.heldBy(household).count { it.topic !in universal }
      counts.getOrPut(trade) { ArrayList() }.add(held)
    }

    return counts
      .filterValues { it.size >= MIN_TRADE_SAMPLE }
      .mapValues { (_, held) -> held.average() }
  }

  /**
   * A stand-in for the occupation catalogue, so this test needs no Spring.
   *
   * Only the profiles have to match what ships - the *names* are the test's own, and grouping by them is
   * only a way to ask whether two differently-weighted groups came out ordered.
   */
  private fun tradeOf(household: Household): String {
    return when {
      household.sector == Sector.MILITARY -> GUARD
      household.sector == Sector.CLERGY -> PRIEST
      household.sector == Sector.FARM -> FARMER
      else -> OTHER
    }
  }

  private fun profileFor(household: Household): KnowledgeProfile {
    return when (tradeOf(household)) {
      GUARD -> KnowledgeProfile(interests = setOf(EventKind.BATTLE, EventKind.SIEGE, EventKind.WAR_DECLARED))
      PRIEST -> KnowledgeProfile(interests = setOf(EventKind.SHRINE_RAISED, EventKind.RITE_PERFORMED))
      FARMER -> KnowledgeProfile(interests = setOf(EventKind.FAMINE, EventKind.FLOOD))
      else -> KnowledgeProfile.ORDINARY
    }
  }

  private fun knowledgeOf(world: GeneratedWorld, settlement: Int, summary: PopulationSummary): TownKnowledge {
    return TownKnowledge.of(
      generated = world,
      settlement = settlement,
      summary = summary,
      worldSeed = WORLD_SEED,
      householdAt = { Households.one(summary, it) },
      profileOf = { profileFor(it) },
      positions = positionsOf(world),
    )
  }

  private fun positionsOf(world: GeneratedWorld): Map<Int, Vec2d> {
    return POSITIONS.getOrPut(world) { SettlementLoreService.settlementPositions(world) }
  }

  private fun build(seed: Long): GeneratedWorld {
    return WORLDS.getOrPut(seed) {
      StandardWorld.build(WorldConfig(seed = seed, widthCells = CELLS, heightCells = CELLS))
    }
  }

  private fun forEachInhabitedTown(world: GeneratedWorld, block: (Int, PopulationSummary) -> Unit) {
    for (record in world.world.chronicle.settlements) {
      if (record.isRuin || !record.wasFounded) continue

      val summary = EconomyProbe.summaryFor(world.world, record.index) ?: continue
      if (summary.householdCount <= 0) continue

      block(record.index, summary)
    }
  }

  private companion object {

    /**
     * The worlds, built once for the whole class.
     *
     * JUnit makes a fresh instance per test method, so an instance field would rebuild every world for
     * every test - six tests times three seeds is fifteen generations of a 256-cell world. That is slow
     * on its own and, sharing a JVM with the rest of the suite, enough memory pressure to fail a
     * timing-sensitive scenario test somewhere else entirely. It did.
     */
    val WORLDS = HashMap<Long, GeneratedWorld>()

    val POSITIONS = HashMap<GeneratedWorld, Map<Int, Vec2d>>()

    /** `SettlementLoreTest`'s size, and for its reason: a smaller world rarely has anything to remember. */
    const val CELLS = 256

    /** Below this a town cannot tell "one household" from "most of them", so the tier tests skip it. */
    const val MEANINGFUL_TOWN = 20

    /** Fewer households than this of one trade is a sample of one person's luck, not a trade. */
    const val MIN_TRADE_SAMPLE = 4

    /**
     * How far one trade's share of the news may sit above another's.
     *
     * Above one because interests are supposed to move it, and well under the order-of-magnitude a reach
     * multiplier would produce. This is the number to look at if the model is ever retuned.
     */
    const val MAX_TRADE_GAP = 2.5

    /** Contested memories a town needs before "somebody knows all of them" means anything. */
    const val RICH_TOWN = 10

    /** How often a town may have a know-it-all before asking around stops being the point. */
    const val MAX_KNOW_ALL_RATE = 0.1

    const val WORLD_SEED = 99L

    const val GUARD = "guard"
    const val PRIEST = "priest"
    const val FARMER = "farmer"
    const val OTHER = "other"

    val SEEDS = listOf(1L, 3L, 42L)
  }
}
