package net.bestia.zone.questgen

import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.entity.observe.WorldTarget
import net.bestia.zone.fact.FactRef
import net.bestia.zone.quest.ObjectiveKind
import net.bestia.zone.quest.ObjectiveScope
import net.bestia.zone.quest.QuestDraft
import net.bestia.zone.quest.QuestOrigin
import net.bestia.zone.questgen.plan.BindSlot
import net.bestia.zone.questgen.plan.Binding
import net.bestia.zone.questgen.plan.QuestCatalogue
import net.bestia.zone.questgen.plan.QuestPlanner
import net.bestia.zone.questgen.plan.QuestWorld
import net.bestia.zone.questgen.seed.QuestSeed
import net.bestia.zone.text.TextArg
import net.bestia.zone.townsfolk.rumour.RumourKind
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The planner on a world made by hand: one lost blade, and whatever each test leaves of the things around it. */
class QuestPlannerTest {

  private val world = HandMadeWorld()
  private val planner = QuestPlanner(QuestCatalogue().apply { load() }, world)

  @Test
  fun `a world with everything gives the long chain, a choice, and the way home after it`() {
    val plan = assertNotNull(planner.plan(BLADE).plan)

    assertEquals(listOf("hear_legend", "read_epitaph", "dig_relic", "decide", "return_relic"), plan.steps.map { it.pattern })
    assertEquals(listOf(0, 1, 2, 3, 4), plan.steps.map { it.objective.stage })
    assertEquals(NEAR_TELLER, plan.startsAt)
    assertEquals(listOf("keep", "return"), plan.steps[3].objective.choices.map { it.id })
    assertEquals("return", plan.steps[4].objective.afterChoice)
    assertEquals(RELIC_ITEM, plan.steps[4].objective.itemId)
    assertEquals(TextArg.Name("Ashbrand"), plan.title.args["artifact"])
    assertEquals(TextArg.Name("Orwen"), plan.steps[1].objective.text!!.args["keeper"])
  }

  @Test
  fun `the quest core takes every plan as it is`() {
    val plan = assertNotNull(planner.plan(BLADE).plan)

    QuestDraft(QuestOrigin.BLACKBOARD, plan.title, plan.suggestedLevel, Instant.MAX, plan.steps.map { it.objective }, rewards = emptyList())
  }

  @Test
  fun `without the keeper's grave the legend alone leads to the dig`() {
    world.remove(BindSlot.KEEPER_GRAVE)

    val plan = assertNotNull(planner.plan(BLADE).plan)

    assertEquals(listOf("hear_legend", "dig_relic", "decide", "return_relic"), plan.steps.map { it.pattern })
  }

  @Test
  fun `without a standing home temple nothing is decided`() {
    world.remove(BindSlot.HOME_TEMPLE)

    val plan = assertNotNull(planner.plan(BLADE).plan)

    assertEquals(ObjectiveKind.EXAMINE, plan.steps.last().objective.kind)
    assertEquals("dig_relic", plan.steps.last().pattern)
  }

  @Test
  fun `no payoff or nobody to start with is no quest, and the planner says what is missing`() {
    world.remove(BindSlot.RELIC_CACHE)
    val noCache = planner.plan(BLADE)
    assertNull(noCache.plan)
    assertEquals(setOf(BindSlot.RELIC_CACHE), noCache.missing)

    val nobodyTells = HandMadeWorld().apply { remove(BindSlot.LEGEND_TELLER) }
    assertEquals(setOf(BindSlot.LEGEND_TELLER), QuestPlanner(QuestCatalogue().apply { load() }, nobodyTells).plan(BLADE).missing)
  }

  @Test
  fun `where no legend is told, a house that remembers the loss starts the quest`() {
    world.remove(BindSlot.LEGEND_TELLER)
    world.remove(BindSlot.KEEPER_GRAVE)
    world.add(BindSlot.LOSS_WITNESS, Binding(WITNESS, Vec2d(2_000.0, 0.0), teaches = listOf(FactRef.event(30))))

    val plan = assertNotNull(planner.plan(BLADE).plan)

    assertEquals(listOf("hear_of_loss", "dig_relic"), plan.steps.map { it.pattern }.take(2))
    assertEquals(WITNESS, plan.startsAt)
  }

  @Test
  fun `a teller another plan already uses is passed over for the next one`() {
    val plan = assertNotNull(planner.plan(BLADE, taken = setOf(NEAR_TELLER)).plan)

    assertEquals(FAR_TELLER, plan.startsAt)
  }

  @Test
  fun `a town that lacks a good asks all comers for it in one shared step`() {
    world.add(
      BindSlot.TOWN_NEED,
      Binding(WorldTarget.settlement(6), Vec2d(0.0, 0.0), mapOf("town" to TextArg.Name("Hale")), itemId = IRON_ORE, amount = 40),
    )
    val shortage = QuestSeed(
      QuestSeed.Kind.TOWN_SHORTAGE, Actor(ActorType.SETTLEMENT, 6), uniqueness = QuestSeed.Uniqueness.SHARED,
      need = QuestSeed.Need(IRON_ORE, 40),
    )

    val plan = assertNotNull(planner.plan(shortage).plan)

    val deliver = plan.steps.single().objective
    assertEquals(ObjectiveKind.DELIVER_ITEM to ObjectiveScope.SHARED, deliver.kind to deliver.scope)
    assertEquals(IRON_ORE to 40, deliver.itemId to deliver.required)
    assertNull(deliver.text, "the client words a delivery from its item")
    assertEquals(WorldTarget.settlement(6), plan.startsAt)
    QuestDraft(QuestOrigin.TOWN, plan.title, plan.suggestedLevel, Instant.MAX, plan.steps.map { it.objective }, rewards = emptyList())
  }

  @Test
  fun `news is heard in the town that has it and told in the town that has not`() {
    val teller = WorldTarget.townsfolk(40)
    world.add(BindSlot.NEWS_TELLER, Binding(teller, Vec2d(0.0, 0.0), mapOf("from" to TextArg.Name("Hale"))))
    world.add(BindSlot.NEWS_LISTENER, Binding(WorldTarget.settlement(6), Vec2d(9_000.0, 0.0), mapOf("to" to TextArg.Name("Dunmere"))))
    val news = QuestSeed(
      QuestSeed.Kind.NEWS, Actor(ActorType.SETTLEMENT, 6), others = listOf(Actor(ActorType.SETTLEMENT, 4)),
      uniqueness = QuestSeed.Uniqueness.PERSONAL, news = QuestSeed.News(12, RumourKind.TOWN_FELL),
    )

    val plan = assertNotNull(planner.plan(news).plan)

    assertEquals(listOf("hear_news", "tell_news"), plan.steps.map { it.pattern })
    assertEquals(teller, plan.startsAt)
    assertEquals(WorldTarget.settlement(6), plan.steps[1].objective.target)
    assertEquals(TextArg.Name("Dunmere"), plan.title.args["to"])
    QuestDraft(QuestOrigin.WORLD, plan.title, plan.suggestedLevel, Instant.MAX, plan.steps.map { it.objective }, rewards = emptyList())
  }

  @Test
  fun `the same world gives the same plan`() {
    assertEquals(planner.plan(BLADE).plan, planner.plan(BLADE).plan)
  }

  private class HandMadeWorld : QuestWorld {

    private val things = mutableMapOf(
      BindSlot.LEGEND_TELLER to listOf(
        Binding(NEAR_TELLER, Vec2d(1_000.0, 0.0), mapOf("town" to TextArg.Name("Hale"))),
        Binding(FAR_TELLER, Vec2d(9_000.0, 0.0), mapOf("town" to TextArg.Name("Dunmere"))),
      ),
      BindSlot.KEEPER_GRAVE to listOf(
        Binding(
          WorldTarget.grave(7), Vec2d(1_200.0, 0.0), mapOf("keeper" to TextArg.Name("Orwen")),
          teaches = listOf(FactRef(FactRef.Subject.FIGURE, 7, FactRef.Aspect.NAME)),
        ),
      ),
      BindSlot.RELIC_CACHE to listOf(Binding(WorldTarget.cache(55), Vec2d(3_000.0, 0.0))),
      BindSlot.HOME_TEMPLE to listOf(Binding(WorldTarget.settlement(2), Vec2d(5_000.0, 0.0), itemId = RELIC_ITEM)),
    )

    fun remove(slot: BindSlot) {
      things.remove(slot)
    }

    fun add(slot: BindSlot, binding: Binding) {
      things[slot] = listOf(binding)
    }

    override fun bindings(slot: BindSlot, seed: QuestSeed): List<Binding> {
      return things[slot].orEmpty()
    }

    override fun argsOf(seed: QuestSeed): Map<String, TextArg> {
      return mapOf("artifact" to TextArg.Name("Ashbrand"))
    }

    override fun levelOf(seed: QuestSeed): Int {
      return 80
    }
  }

  private companion object {
    const val RELIC_ITEM = 900L
    const val IRON_ORE = 8L
    val BLADE = QuestSeed(QuestSeed.Kind.LOST_RELIC, Actor(ActorType.ARTIFACT, 3), uniqueness = QuestSeed.Uniqueness.UNIQUE)
    val NEAR_TELLER = WorldTarget.townsfolk(1)
    val FAR_TELLER = WorldTarget.townsfolk(2)
    val WITNESS = WorldTarget.townsfolk(3)
  }
}
