package net.bestia.zone.item.script

import io.mockk.mockk
import net.bestia.zone.battle.FixedRandom
import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.battle.status.scripts.AgilityElixir
import net.bestia.zone.battle.status.scripts.DexterityElixir
import net.bestia.zone.battle.status.scripts.IntelligenceElixir
import net.bestia.zone.battle.status.scripts.Poison
import net.bestia.zone.battle.status.scripts.StrengthElixir
import net.bestia.zone.battle.status.scripts.VitalityElixir
import net.bestia.zone.battle.status.scripts.WillpowerElixir
import net.bestia.zone.boot.StatusEffectImporterBootRunner
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Alchemist's cures and elixirs: what they wash out, what they lay on, and when they stay in the bag. */
class AlchemyBrewsTest {

  private val definitions = StatusEffectDefinitionRegistry().also { StatusEffectImporterBootRunner(it).run() }
  private val scripts = StatusEffectScriptRegistry(
    listOf(Poison(), StrengthElixir(), VitalityElixir(), IntelligenceElixir(), DexterityElixir(), AgilityElixir(), WillpowerElixir())
  )
  private val statusEffects = StatusEffectService(definitions, scripts, FixedRandom(0f))
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val world = testWorld()

  @Test
  fun `an antidote washes out poison and heals a little`() {
    val drinker = patient()
    statusEffects.applyEffect(world, drinker, StatusEffectId.POISON.id, level = 1)

    assertTrue(AntidoteScript(messages, statusEffects).execute(world, drinker, ScriptArgs.EMPTY))

    assertFalse(effectsOf(drinker).hasEffect(StatusEffectId.POISON.id))
    assertEquals(1 + 30, world.get(drinker, Health::class)!!.current)
  }

  /** Nothing to cure and nothing to restore: the vial is not wasted. */
  @Test
  fun `a cure with nothing to wash out stays in the bag`() {
    val healthy = world.createEntity { id -> add(id, StatusEffects()) }

    assertFalse(ClarityDraughtScript(statusEffects).execute(world, healthy, ScriptArgs.EMPTY))
  }

  @Test
  fun `an elixir raises its attribute for ten minutes and a greater one twice as much`() {
    val drinker = patient()

    assertTrue(ElixirOfMightScript(statusEffects).execute(world, drinker, ScriptArgs.EMPTY))
    val might = effectsOf(drinker).activeEffects.single { it.definitionId == StatusEffectId.STRENGTH_ELIXIR.id }
    assertEquals(600f, might.remainingSeconds)
    assertEquals(BASE.strength + 5, strengthUnder(1))

    assertTrue(GreaterElixirOfMightScript(statusEffects).execute(world, drinker, ScriptArgs.EMPTY))
    assertEquals(2, effectsOf(drinker).activeEffects.single { it.definitionId == StatusEffectId.STRENGTH_ELIXIR.id }.level)
    assertEquals(BASE.strength + 10, strengthUnder(2))
  }

  @Test
  fun `a weaker elixir leaves a stronger one in place`() {
    val drinker = patient()

    GreaterElixirOfMightScript(statusEffects).execute(world, drinker, ScriptArgs.EMPTY)
    ElixirOfMightScript(statusEffects).execute(world, drinker, ScriptArgs.EMPTY)

    assertEquals(2, effectsOf(drinker).activeEffects.single { it.definitionId == StatusEffectId.STRENGTH_ELIXIR.id }.level)
  }

  @Test
  fun `the philosophers draught raises all six attributes at once`() {
    val drinker = patient()

    assertTrue(PhilosophersDraughtScript(statusEffects).execute(world, drinker, ScriptArgs.EMPTY))

    val elixirs = listOf(
      StatusEffectId.STRENGTH_ELIXIR, StatusEffectId.VITALITY_ELIXIR, StatusEffectId.INTELLIGENCE_ELIXIR,
      StatusEffectId.DEXTERITY_ELIXIR, StatusEffectId.AGILITY_ELIXIR, StatusEffectId.WILLPOWER_ELIXIR,
    )
    assertEquals(elixirs.map { it.id to 3 }.toSet(), effectsOf(drinker).activeEffects.map { it.definitionId to it.level }.toSet())
  }

  /** Thrown by a Potion Pitcher at half strength or more, a cure still cures whole. */
  @Test
  fun `a thrown cure cures whole at any strength`() {
    val ally = patient()
    statusEffects.applyEffect(world, ally, StatusEffectId.POISON.id, level = 1)

    assertTrue(AntidoteScript(messages, statusEffects).restore(world, ally, strengthPercent = 150))

    assertFalse(effectsOf(ally).hasEffect(StatusEffectId.POISON.id))
    assertEquals(1 + 45, world.get(ally, Health::class)!!.current)
  }

  @Test
  fun `the restoratives and cures are potions and the elixirs are not`() {
    assertTrue(SlimSupremeHealthPotionScript(messages).isPotion)
    assertTrue(PanaceaScript(statusEffects).isPotion)
    assertFalse(ElixirOfMightScript(statusEffects).isPotion)
    assertFalse(SwiftnessPotionScript(statusEffects).isPotion)
  }

  private fun strengthUnder(level: Int): Int {
    val context = StatusValueRecalcContext(BASE, baseSpeed = 1f)
    StrengthElixir().apply(world, NOBODY, context, level, null)

    return context.strength
  }

  private fun patient(): EntityId = world.createEntity { id ->
    add(id, Health(1, 500))
    add(id, StatusEffects())
  }

  private fun effectsOf(id: EntityId) = world.get(id, StatusEffects::class)!!

  private companion object {
    val BASE = StatusValues(strength = 10, vitality = 10, intelligence = 10, agility = 10, willpower = 10, dexterity = 10)
    const val NOBODY: EntityId = 0L
  }
}
