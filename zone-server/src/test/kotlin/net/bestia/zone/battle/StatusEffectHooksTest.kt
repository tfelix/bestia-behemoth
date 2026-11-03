package net.bestia.zone.battle

import net.bestia.zone.battle.ecs.effects.StatusEffectDurationSystem
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.ResistStat
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectPolarity
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusEffectHooksTest {

  /** Records what the service told it, and soaks every hit up to [SOAK]. */
  private class Recording : StatusEffectScript {
    val applied = mutableListOf<EntityId>()
    val removed = mutableListOf<RemovalReason>()

    override fun durationSeconds(level: Int): Double {
      return 10.0
    }

    override fun onApply(context: StatusEffectHookContext) {
      applied.add(context.hostId)
    }

    override fun onRemove(context: StatusEffectHookContext, reason: RemovalReason) {
      removed.add(reason)
    }

    override fun onDamageTaken(context: StatusEffectHookContext, amount: Int): Int {
      return (amount - SOAK).coerceAtLeast(0)
    }
  }

  private val script = Recording()
  private val world = testWorld()

  private val definitions = StatusEffectDefinitionRegistry().apply {
    load(
      listOf(
        StatusEffectDefinition(
          id = EFFECT.id, identifier = EFFECT.name, isSyncedToClient = true, script = "Recording",
          polarity = StatusEffectPolarity.NEUTRAL, resist = listOf(ResistStat.VIT, ResistStat.STR)
        )
      )
    )
  }
  private val scripts = StatusEffectScriptRegistry(listOf(script))

  private fun service(roll: Double = 0.0): StatusEffectService {
    return StatusEffectService(definitions, scripts, object : Random() {
      override fun nextDouble(): Double = roll
    })
  }

  @Test
  fun `onApply runs when the effect lands, not when it is refreshed`() {
    val target = world.createEntity { }
    val service = service()

    service.applyEffect(world, target, EFFECT, level = 1)
    service.applyEffect(world, target, EFFECT, level = 1)

    assertEquals(listOf(target), script.applied)
  }

  @Test
  fun `removing an effect tells its script why`() {
    val target = world.createEntity { }
    val service = service()
    service.applyEffect(world, target, EFFECT, level = 1)

    assertTrue(service.removeEffect(world, target, EFFECT.id, RemovalReason.DISPELLED))

    assertEquals(listOf(RemovalReason.DISPELLED), script.removed)
    assertFalse(world.getOrThrow(target, StatusEffects::class).hasEffect(EFFECT.id))
  }

  @Test
  fun `an effect that runs out is told it expired`() {
    val service = service()
    val ticking = testWorld(systems = listOf(StatusEffectDurationSystem(service, definitions, scripts)))
    val target = ticking.createEntity { }
    service.applyEffect(ticking, target, EFFECT, level = 1, durationSeconds = 1.0)

    ticking.tick(1.5f)

    assertEquals(listOf(RemovalReason.EXPIRED), script.removed)
  }

  @Test
  fun `an effect on the target changes how much of a hit lands`() {
    val target = world.createEntity { }
    val service = service()
    service.applyEffect(world, target, EFFECT, level = 1)

    assertEquals(25, service.onDamageTaken(world, target, 25 + SOAK))
  }

  @Test
  fun `a certain inflict on an unresisting target lasts its full duration`() {
    val target = world.createEntity { }

    assertTrue(service().tryInflict(world, target, EFFECT, level = 1, sourceEntityId = null, chancePercent = 100))

    assertEquals(10f, remainingSecondsOf(target))
  }

  @Test
  fun `the listed attributes cut the chance and shorten the effect`() {
    // VIT 150 resists half: a 100% chance becomes 50%, and the 10 s effect lasts 7.5 s.
    val sturdy = withVitality(150)

    assertFalse(service(roll = 0.55).tryInflict(world, sturdy, EFFECT, 1, sourceEntityId = null, chancePercent = 100))
    assertTrue(service(roll = 0.45).tryInflict(world, sturdy, EFFECT, 1, sourceEntityId = null, chancePercent = 100))
    assertEquals(7.5f, remainingSecondsOf(sturdy))
  }

  private fun withVitality(vitality: Int): EntityId {
    val values = StatusValues(
      strength = 0, vitality = vitality, intelligence = 0, agility = 0, willpower = 0, dexterity = 0
    )

    return world.createEntity { id -> add(id, CombatStats(CombatValues.ofBase(level = 1, base = values))) }
  }

  private fun remainingSecondsOf(target: EntityId): Float {
    return world.getOrThrow(target, StatusEffects::class).activeEffects.single().remainingSeconds
  }

  private companion object {
    val EFFECT = StatusEffectId.CRIPPLE
    const val SOAK = 10
  }
}
