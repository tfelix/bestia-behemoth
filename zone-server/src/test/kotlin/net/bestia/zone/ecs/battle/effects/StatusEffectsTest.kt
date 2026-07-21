package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.status.StackBehavior
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
}
