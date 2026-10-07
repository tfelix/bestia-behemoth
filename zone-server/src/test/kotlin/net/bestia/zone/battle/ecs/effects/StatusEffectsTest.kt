package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectPolarity
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.sync.SyncTargets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatusEffectsTest {

  @Test
  fun `applying an effect adds an active instance`() {
    val effects = StatusEffects()

    effects.applyEffect(
      definitionId = 1L,
      stackBehavior = StackBehavior.REFRESH_DURATION,
      level = 1,
      sourceEntityId = null,
      durationSeconds = 10.0,
      isSyncedToClient = true
    )

    assertEquals(1, effects.activeEffects.size)
    assertEquals(1L, effects.activeEffects.first().definitionId)
  }

  @Test
  fun `REFRESH_DURATION resets remaining duration instead of stacking`() {
    val effects = StatusEffects()

    effects.applyEffect(1L, StackBehavior.REFRESH_DURATION, 1, null, 10.0, true)
    effects.activeEffects.first().remainingSeconds = 2f
    effects.applyEffect(1L, StackBehavior.REFRESH_DURATION, 1, null, 10.0, true)

    assertEquals(1, effects.activeEffects.size)
    assertEquals(10f, effects.activeEffects.first().remainingSeconds)
  }

  @Test
  fun `STACK_INDEPENDENT allows multiple instances`() {
    val effects = StatusEffects()

    effects.applyEffect(1L, StackBehavior.STACK_INDEPENDENT, 1, null, 10.0, true)
    effects.applyEffect(1L, StackBehavior.STACK_INDEPENDENT, 1, null, 10.0, true)

    assertEquals(2, effects.activeEffects.size)
  }

  @Test
  fun `IGNORE_IF_PRESENT does not add a second instance`() {
    val effects = StatusEffects()

    effects.applyEffect(1L, StackBehavior.IGNORE_IF_PRESENT, 1, null, 10.0, true)
    effects.applyEffect(1L, StackBehavior.IGNORE_IF_PRESENT, 5, null, 10.0, true)

    assertEquals(1, effects.activeEffects.size)
    assertEquals(1, effects.activeEffects.first().level)
  }

  @Test
  fun `REPLACE_IF_STRONGER only replaces when the new level is higher`() {
    val effects = StatusEffects()

    effects.applyEffect(1L, StackBehavior.REPLACE_IF_STRONGER, 3, null, 10.0, true)
    effects.applyEffect(1L, StackBehavior.REPLACE_IF_STRONGER, 2, null, 10.0, true)
    assertEquals(3, effects.activeEffects.first().level)

    effects.applyEffect(1L, StackBehavior.REPLACE_IF_STRONGER, 5, null, 10.0, true)
    assertEquals(1, effects.activeEffects.size)
    assertEquals(5, effects.activeEffects.first().level)
  }

  @Test
  fun `tickDown removes expired effects and reports whether anything expired`() {
    val effects = StatusEffects()
    effects.applyEffect(1L, StackBehavior.REFRESH_DURATION, 1, null, 1.0, true)

    assertFalse(effects.tickDown(0.5f))
    assertEquals(1, effects.activeEffects.size)

    assertTrue(effects.tickDown(0.6f))
    assertTrue(effects.activeEffects.isEmpty())
  }

  @Test
  fun `toEntityMessage filters out effects not synced to the client`() {
    val effects = StatusEffects()
    effects.applyEffect(1L, StackBehavior.STACK_INDEPENDENT, 1, null, 10.0, isSyncedToClient = true)
    effects.applyEffect(2L, StackBehavior.STACK_INDEPENDENT, 1, null, 10.0, isSyncedToClient = false)

    val message = effects.toEntityMessage(entityId = 42L) as StatusEffectsComponentSMSG

    assertEquals(42L, message.entityId)
    assertEquals(1, message.effects.size)
    assertEquals(1L, message.effects.first().effectId)
  }

  @Test
  fun `a player's effects are told to everybody in range, not only to the owner`() {
    val world = testWorld()
    val player = world.createEntity { id -> add(id, Account(accountId = 3L)) }

    assertEquals(SyncTargets.PublicInRange, StatusEffects().syncTargets(world, player))
  }

  @Test
  fun `the client is told which effects are debuffs`() {
    val effects = StatusEffects()
    effects.applyEffect(1L, StackBehavior.STACK_INDEPENDENT, 1, null, 10.0, true, polarity = StatusEffectPolarity.DEBUFF)
    effects.applyEffect(2L, StackBehavior.STACK_INDEPENDENT, 1, null, 10.0, true, polarity = StatusEffectPolarity.BUFF)

    val message = effects.toEntityMessage(entityId = 42L) as StatusEffectsComponentSMSG

    assertEquals(mapOf(1L to true, 2L to false), message.effects.associate { it.effectId to it.debuff })
  }

  @Test
  fun `restore adds stored effects but keeps a live one with the same id`() {
    val effects = StatusEffects()
    effects.applyEffect(1L, StackBehavior.IGNORE_IF_PRESENT, 3, null, 10.0, true)

    effects.restore(
      listOf(
        ActiveStatusEffect(definitionId = 1L, level = 1, remainingSeconds = 2f),
        ActiveStatusEffect(definitionId = 2L, level = 1, remainingSeconds = 2f),
      )
    )

    assertEquals(listOf(1L, 2L), effects.activeEffects.map { it.definitionId })
    assertEquals(3, effects.activeEffects.first().level, "the stored copy replaced the live effect")
  }

  @Test
  fun `a refresh only moves the clock and tells the client only about a visible effect`() {
    val effects = StatusEffects()
    effects.applyEffect(1L, StackBehavior.REFRESH_DURATION, 1, null, 10.0, isSyncedToClient = false)
    effects.applyEffect(2L, StackBehavior.REFRESH_DURATION, 1, null, 10.0, isSyncedToClient = true)

    effects.dirtyFlag.clear()
    assertEquals(StatusEffects.Change.REFRESHED, effects.applyEffect(1L, StackBehavior.REFRESH_DURATION, 1, null, 10.0, false))
    assertFalse(effects.dirtyFlag.isSet, "a hidden effect's new clock was queued for the client")

    assertEquals(StatusEffects.Change.REFRESHED, effects.applyEffect(2L, StackBehavior.REFRESH_DURATION, 1, null, 10.0, true))
    assertTrue(effects.dirtyFlag.isSet, "the client would keep counting down the old clock")
  }

  @Test
  fun `a repeat that is ignored changes nothing at all`() {
    val effects = StatusEffects()
    assertEquals(StatusEffects.Change.CHANGED, effects.applyEffect(1L, StackBehavior.IGNORE_IF_PRESENT, 1, null, 10.0, true))

    effects.dirtyFlag.clear()

    assertEquals(StatusEffects.Change.UNCHANGED, effects.applyEffect(1L, StackBehavior.IGNORE_IF_PRESENT, 1, null, 10.0, true))
    assertFalse(effects.dirtyFlag.isSet)
  }

  @Test
  fun `a shield holds while its effect is active`() {
    val effects = StatusEffects()
    effects.applyEffect(1L, StackBehavior.REFRESH_DURATION, 1, null, 1.0, false, shield = HarmShield.ALL)

    assertTrue(effects.hasShield(HarmShield.ALL))

    effects.tickDown(2f)

    assertFalse(effects.hasShield(HarmShield.ALL))
  }
}
