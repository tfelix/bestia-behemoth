package net.bestia.zone.ai.rumour

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pop.EconomyProbe
import net.bestia.worldgen.pop.Households
import net.bestia.worldgen.pop.PopulationSummary
import net.bestia.zone.ai.knowledge.Knowledge
import net.bestia.zone.ai.knowledge.KnowledgeProfile
import net.bestia.zone.ai.knowledge.Locality
import net.bestia.zone.ai.knowledge.TownKnowledge
import net.bestia.zone.dialog.conversation.Topics
import net.bestia.zone.world.GeneratedWorlds
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That recent news is shared out by the same rules as the chronicle, and cannot be confused with it.
 *
 * The join this whole feature rests on is that nothing downstream can tell a rumour from a thousand-year
 * -old founding. Two things have to hold for that: they must go through the same assignment, and their
 * topic ids must never collide - an option id is the only thing a conversation stores, so a rumour that
 * answered to an event's id would show a player the wrong memory entirely.
 */
class RumourKnowledgeTest {

  @Test
  fun `a rumour's topic can never be mistaken for an event's`() {
    val world = GeneratedWorlds.of(SEED)
    val eventIds = world.world.chronicle.events.map { it.id }.toSet()

    // Every row id the registry can hand out, against every event the world logged.
    for (id in 1L..500L) {
      val topic = rumour(id = id).toKnowledge(DAY, PRESENT_YEAR, 1).topic

      assertTrue(topic !in eventIds, "rumour $id collides with a chronicle event id")
    }

    // The other end of the same promise: the offset has to clear the chronicle, not merely miss it on
    // one seed. A world that logged fifty thousand events would start colliding silently.
    val highest = eventIds.maxOrNull() ?: 0
    assertTrue(
      highest < Rumour.TOPIC_BASE,
      "seed $SEED logged event id $highest, at or past the ${Rumour.TOPIC_BASE} rumours start from"
    )
  }

  /**
   * The half that disjointness alone does not buy, and the bug this test was written for.
   *
   * A conversation option is `Topics.KNOWLEDGE + topic`, and `Topics.owns` is a range check. Negating
   * the row id gives perfectly disjoint topics and lands every option in the *previous* provider's
   * range, where it is claimed by a provider that has never heard of it and answered with silence - a
   * player clicks the news and nothing happens. Nothing about who-knows-what would show it.
   */
  @Test
  fun `a rumour's option id comes back to the provider that offered it`() {
    for (id in 1L..500L) {
      val topic = rumour(id = id).toKnowledge(DAY, PRESENT_YEAR, 1).topic
      val optionId = Topics.KNOWLEDGE + topic

      assertTrue(
        Topics.owns(Topics.KNOWLEDGE, optionId),
        "option $optionId for rumour $id is not in the knowledge provider's range"
      )
      assertEquals(topic, Topics.localOf(Topics.KNOWLEDGE, optionId))
    }
  }

  @Test
  fun `a rumour becomes a memory with everything a memory needs`() {
    val knowledge = rumour().toKnowledge(DAY, PRESENT_YEAR, 2)

    assertEquals("RUMOUR_BOSS_SLAIN", knowledge.key)
    assertEquals(PRESENT_YEAR, knowledge.year)
    assertEquals(Locality.NEARBY, knowledge.locality)
    assertEquals(2, knowledge.variants)
    assertTrue(knowledge.importance > 0, "fresh news should be worth something")
  }

  @Test
  fun `fresh news is held by more of the town than spent news`() {
    val world = GeneratedWorlds.of(SEED)

    var checked = 0
    forEachInhabitedTown(world) { settlement, summary ->
      if (summary.householdCount < MEANINGFUL_TOWN || checked >= TOWNS) return@forEachInhabitedTown
      checked++

      val fresh = holdersOf(world, settlement, summary, rumour(id = 1).toKnowledge(DAY, PRESENT_YEAR, 1))
      val spent = holdersOf(world, settlement, summary, rumour(id = 2).toKnowledge(NEARLY_OVER, PRESENT_YEAR, 1))

      // Strictly more, and more than one: `fresh >= spent` would pass with both at a single household,
      // which is what a decay that never reached the share tiers would look like.
      assertTrue(fresh > 1, "fresh news in a town of ${summary.householdCount} reached only $fresh household(s)")
      assertTrue(
        fresh > spent,
        "news should thin out as it ages: $fresh households when fresh, $spent when spent"
      )
      assertTrue(spent >= 1, "somebody saw it happen, so spent news must still have a holder")
    }

    assertTrue(checked > 0, "no town on seed $SEED was big enough to check")
  }

  /**
   * The tier a player is meant to notice: news nearly out of its life is down to one household.
   *
   * That is the same "somebody saw it happen" the bottom of the chronicle table produces, and getting it
   * for free from the decay is the reason a rumour goes through the same assignment at all.
   */
  @Test
  fun `news at the end of its life is down to a single household`() {
    val world = GeneratedWorlds.of(SEED)

    var checked = 0
    forEachInhabitedTown(world) { settlement, summary ->
      if (summary.householdCount < MEANINGFUL_TOWN || checked >= TOWNS) return@forEachInhabitedTown
      checked++

      val holders = holdersOf(world, settlement, summary, rumour(id = 3).toKnowledge(NEARLY_OVER, PRESENT_YEAR, 1))

      assertEquals(1, holders, "spent news should be down to whoever saw it")
    }

    assertTrue(checked > 0)
  }

  @Test
  fun `a town with news but no history still knows the news`() {
    val world = GeneratedWorlds.of(SEED)

    // The guard that used to return early on an empty chronicle pool. A young town with nothing in its
    // past would otherwise have been silent about the thing that happened outside it last night.
    var checked = 0
    forEachInhabitedTown(world) { settlement, summary ->
      if (checked >= TOWNS) return@forEachInhabitedTown
      checked++

      val town = TownKnowledge.of(
        generated = world,
        settlement = settlement,
        summary = summary,
        worldSeed = WORLD_SEED,
        householdAt = { Households.one(summary, it) },
        profileOf = { KnowledgeProfile.ORDINARY },
        rumours = listOf(rumour().toKnowledge(DAY, PRESENT_YEAR, 1)),
        nearbyRange = 0.0,
      )

      assertTrue(town.all().any { it.key == "RUMOUR_BOSS_SLAIN" }, "settlement $settlement lost its news")
    }

    assertTrue(checked > 0)
  }

  private fun holdersOf(
    world: GeneratedWorld,
    settlement: Int,
    summary: PopulationSummary,
    news: Knowledge,
  ): Int {
    val town = TownKnowledge.of(
      generated = world,
      settlement = settlement,
      summary = summary,
      worldSeed = WORLD_SEED,
      householdAt = { Households.one(summary, it) },
      profileOf = { KnowledgeProfile.ORDINARY },
      rumours = listOf(news),
    )

    if (town.universal().any { it.topic == news.topic }) {
      return summary.householdCount
    }

    return town.holderCounts()[news.topic] ?: 0
  }

  private fun forEachInhabitedTown(world: GeneratedWorld, block: (Int, PopulationSummary) -> Unit) {
    for (record in world.world.chronicle.settlements) {
      if (record.isRuin || !record.wasFounded) continue

      val summary = EconomyProbe.summaryFor(world.world, record.index) ?: continue
      if (summary.householdCount <= 0) continue

      block(record.index, summary)
    }
  }

  private fun rumour(id: Long = 1): Rumour {
    return Rumour(
      id = id,
      settlement = 0,
      kind = RumourKind.BOSS_SLAIN,
      slots = "",
      postedOnDay = 100.0,
      expiresOnDay = 120.0,
      strength = 1.0,
    )
  }

  private companion object {
    val SEED = GeneratedWorlds.SEEDS.first()

    const val WORLD_SEED = 4242L
    const val PRESENT_YEAR = 1100

    /** Just posted. */
    const val DAY = 100.0

    /** One day left of twenty, so its importance has decayed almost to nothing. */
    const val NEARLY_OVER = 119.0

    const val MEANINGFUL_TOWN = 20

    /** Enough towns to be sure it is not one lucky settlement, few enough to stay quick. */
    const val TOWNS = 5
  }
}
