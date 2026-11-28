package net.bestia.zone.item.meal

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.battle.status.scripts.AgilityIncreased
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.eating.Eating
import net.bestia.zone.item.eating.EatingInterrupt
import net.bestia.zone.item.eating.EatingService
import net.bestia.zone.item.eating.EatingSystem
import net.bestia.zone.item.script.ItemScriptExecutionService
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Eating a meal: the channel, the SATISFIED it leaves, and how a second meal replaces the first. */
class MealsTest {

  private val items = mockk<ItemTemplateRegistry> {
    every { idOf(any()) } answers { IDS[firstArg()] }
  }
  private val meals = MealCatalog(items).also { it.loadFrom(MEALS_YML.byteInputStream()); it.warmUp() }

  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val effects = StatusEffectService(
    definitions, StatusEffectScriptRegistry(listOf(Satisfied(meals), AgilityIncreased())), FixedRandom(0f)
  )
  private val scripts = ItemScriptExecutionService(
    emptyList(), listOf(MealScripts(meals, mockk<OutMessageProcessor>(relaxed = true), effects))
  )
  private val cancel = CastCancelService(listOf(EatingInterrupt()))
  private val eating = EatingService(meals, scripts, cancel, mockk(relaxed = true), mockk(relaxed = true), effects)
  private val system = EatingSystem(eating)
  private val world = testWorld()

  @Test
  fun `the real meals file loads`() {
    MealCatalog(items).load()
  }

  @Test
  fun `a meal is eaten over its channel and leaves SATISFIED carrying the meal`() {
    val eater = eater(STEW to 2)

    assertTrue(eating.begin(world, eater, playerItemIdOf(eater, STEW)))
    system.update(world, 5.9f)
    assertEquals(2, amountOf(eater, STEW), "nothing is eaten before the channel is through")
    assertNull(satisfiedOf(eater))

    system.update(world, 0.2f)

    assertNull(world.get(eater, Eating::class))
    assertEquals(1, amountOf(eater, STEW))
    val satisfied = assertNotNull(satisfiedOf(eater))
    assertEquals(STEW.toInt(), satisfied.amount)
    assertEquals(900f, satisfied.remainingSeconds)
    assertEquals(40, world.get(eater, Stamina::class)!!.current)
  }

  @Test
  fun `a channel broken off eats nothing`() {
    val eater = eater(STEW to 1)

    eating.begin(world, eater, playerItemIdOf(eater, STEW))
    system.update(world, 3f)
    cancel.cancelCraft(world, eater)
    system.update(world, 10f)

    assertEquals(1, amountOf(eater, STEW))
    assertNull(satisfiedOf(eater))
  }

  @Test
  fun `a second meal replaces the first whole, even with a shorter timer`() {
    val eater = eater(FEAST to 1, STEW to 1)

    eatNow(eater, FEAST)
    eatNow(eater, STEW)

    val all = world.get(eater, StatusEffects::class)!!.activeEffects.filter { it.definitionId == StatusEffectId.SATISFIED.id }
    assertEquals(1, all.size)
    assertEquals(STEW.toInt(), all.single().amount)
    assertEquals(900f, all.single().remainingSeconds)
  }

  @Test
  fun `SATISFIED grants the bonus of the meal it carries`() {
    val context = StatusValueRecalcContext(BASE, baseSpeed = 1f)
    val effect = net.bestia.zone.battle.ecs.effects.ActiveStatusEffect(
      definitionId = StatusEffectId.SATISFIED.id, level = 1, remainingSeconds = 10f
    ).also { it.amount = FEAST.toInt() }

    Satisfied(meals).apply(world, 0L, context, effect)

    assertEquals(BASE.strength + 3 + 2, context.strength)
    assertEquals(BASE.agility + 2, context.agility)
    assertEquals(20, context.staminaDrainReduction)
    assertEquals(10, context.elementResistance[Element.FIRE])
  }

  @Test
  fun `a meal lays on its other grants`() {
    val eater = eater(FEAST to 1)

    eatNow(eater, FEAST)

    val agility = world.get(eater, StatusEffects::class)!!.activeEffects.single { it.definitionId == StatusEffectId.INCREASE_AGI.id }
    assertEquals(120f, agility.remainingSeconds)
  }

  @Test
  fun `an item that is no meal is not begun`() {
    val eater = eater(OTHER to 1)

    assertFalse(eating.begin(world, eater, playerItemIdOf(eater, OTHER)))
  }

  private fun eatNow(eater: EntityId, itemId: Long) {
    eating.begin(world, eater, playerItemIdOf(eater, itemId))
    system.update(world, meals.channelSeconds + 0.1f)
  }

  private fun eater(vararg held: Pair<Long, Int>): EntityId = world.createEntity { id ->
    add(id, Health(1, 500))
    add(id, Stamina(0, 500))
    add(id, StatusEffects())
    add(id, Inventory(held.map { (itemId, amount) -> Inventory.Item(itemId = itemId, amount = amount) }.toMutableList()))
  }

  private fun playerItemIdOf(eater: EntityId, itemId: Long): Long =
    world.get(eater, Inventory::class)!!.getItems().single { it.itemId == itemId }.playerItemId

  private fun amountOf(eater: EntityId, itemId: Long): Int =
    world.get(eater, Inventory::class)!!.getItems().singleOrNull { it.itemId == itemId }?.amount ?: 0

  private fun satisfiedOf(eater: EntityId) =
    world.get(eater, StatusEffects::class)?.activeEffects?.singleOrNull { it.definitionId == StatusEffectId.SATISFIED.id }

  private companion object {
    const val STEW = 17L
    const val FEAST = 900L
    const val OTHER = 901L
    val IDS = mapOf("hearty_stew" to STEW, "test_feast" to FEAST)
    val BASE = StatusValues(strength = 10, vitality = 10, intelligence = 10, agility = 10, willpower = 10, dexterity = 10)

    val MEALS_YML = """
      channel-seconds: 6.0
      meals:
        - meal: hearty_stew
          duration-seconds: 900
          restore: { health: 60, stamina: 40 }
          bonus: { stamina-regen-percent: 10, vitality: 2 }
        - meal: test_feast
          duration-seconds: 3600
          restore: { stamina: 100 }
          bonus:
            strength: 3
            all-attributes: 2
            stamina-drain-reduction: 20
            element-resistance: { FIRE: 10 }
          grants:
            - { effect: INCREASE_AGI, level: 1, duration-seconds: 120 }
    """.trimIndent()
  }
}
