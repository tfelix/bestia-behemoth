package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectSource
import net.bestia.zone.battle.status.StackBehavior
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatusEffectsTest {

  private fun definition(
    id: Long = 1L,
    showIcon: Boolean = true,
    polarity: StatusEffectSource = StatusEffectSource.BUFF,
    stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION
  ) = StatusEffectDefinition(
    id = id,
    identifier = "TEST_$id",
    polarity = polarity,
    showIcon = showIcon,
    baseDurationSeconds = 10.0,
    stackBehavior = stackBehavior
  )

  @Test
  fun `applying an effect adds an active instance`() {
    val effects = StatusEffects()
    val def = definition()

    effects.applyEffect(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)

    assertEquals(1, effects.activeEffects.size)
    assertEquals(def.id, effects.activeEffects.first().definitionId)
  }

  @Test
  fun `REFRESH_DURATION resets remaining duration instead of stacking`() {
    val effects = StatusEffects()
    val def = definition(stackBehavior = StackBehavior.REFRESH_DURATION)

    effects.applyEffect(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    effects.activeEffects.first().remainingSeconds = 2f
    effects.applyEffect(def, level = 1, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)

    assertEquals(1, effects.activeEffects.size)
    assertEquals(10f, effects.activeEffects.first().remainingSeconds)
  }

  @Test
  fun `STACK_INDEPENDENT allows multiple instances`() {
    val effects = StatusEffects()
    val def = definition(stackBehavior = StackBehavior.STACK_INDEPENDENT)

    effects.applyEffect(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    effects.applyEffect(def, level = 1, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)

    assertEquals(2, effects.activeEffects.size)
  }

  @Test
  fun `IGNORE_IF_PRESENT does not add a second instance`() {
    val effects = StatusEffects()
    val def = definition(stackBehavior = StackBehavior.IGNORE_IF_PRESENT)

    effects.applyEffect(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    effects.applyEffect(def, level = 5, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)

    assertEquals(1, effects.activeEffects.size)
    assertEquals(1, effects.activeEffects.first().level)
  }

  @Test
  fun `REPLACE_IF_STRONGER only replaces when the new level is higher`() {
    val effects = StatusEffects()
    val def = definition(stackBehavior = StackBehavior.REPLACE_IF_STRONGER)

    effects.applyEffect(def, level = 3, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    effects.applyEffect(def, level = 2, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)
    assertEquals(3, effects.activeEffects.first().level)

    effects.applyEffect(def, level = 5, instanceId = 3L, sourceEntityId = null, durationSeconds = 10.0)
    assertEquals(1, effects.activeEffects.size)
    assertEquals(5, effects.activeEffects.first().level)
  }

  @Test
  fun `tickDown removes expired effects`() {
    val effects = StatusEffects()
    val def = definition()
    effects.applyEffect(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 1.0)

    effects.tickDown(0.5f)
    assertEquals(1, effects.activeEffects.size)

    effects.tickDown(0.6f)
    assertTrue(effects.activeEffects.isEmpty())
  }

  @Test
  fun `consume removes a specific instance by id`() {
    val effects = StatusEffects()
    val def = definition(stackBehavior = StackBehavior.STACK_INDEPENDENT)
    effects.applyEffect(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    effects.applyEffect(def, level = 1, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)

    val removed = effects.consume(1L)

    assertTrue(removed)
    assertEquals(1, effects.activeEffects.size)
    assertEquals(2L, effects.activeEffects.first().instanceId)
  }

  @Test
  fun `toEntityMessage filters out effects with showIcon false`() {
    val effects = StatusEffects()
    val visible = definition(id = 1L, showIcon = true)
    val hidden = definition(id = 2L, showIcon = false)

    effects.applyEffect(visible, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    effects.applyEffect(hidden, level = 1, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)

    val message = effects.toEntityMessage(entityId = 42L) as StatusEffectsComponentSMSG

    assertEquals(42L, message.entityId)
    assertEquals(1, message.effects.size)
    assertEquals(visible.id, message.effects.first().effectId)
  }

  @Test
  fun `toEntityMessage marks debuffs`() {
    val effects = StatusEffects()
    val debuff = definition(id = 1L, polarity = StatusEffectSource.DEBUFF)

    effects.applyEffect(debuff, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)

    val message = effects.toEntityMessage(entityId = 1L) as StatusEffectsComponentSMSG
    assertTrue(message.effects.first().debuff)
  }
}
