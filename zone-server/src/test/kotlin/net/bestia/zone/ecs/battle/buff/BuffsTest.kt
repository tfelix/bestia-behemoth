package net.bestia.zone.ecs.battle.buff

import net.bestia.zone.battle.buff.BuffDefinition
import net.bestia.zone.battle.buff.BuffPolarity
import net.bestia.zone.battle.buff.StackBehavior
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuffsTest {

  private fun definition(
    id: Long = 1L,
    showIcon: Boolean = true,
    polarity: BuffPolarity = BuffPolarity.BUFF,
    stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION
  ) = BuffDefinition(
    id = id,
    identifier = "TEST_$id",
    polarity = polarity,
    showIcon = showIcon,
    baseDurationSeconds = 10.0,
    stackBehavior = stackBehavior
  )

  @Test
  fun `applying a buff adds an active instance`() {
    val buffs = Buffs()
    val def = definition()

    buffs.applyBuff(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)

    assertEquals(1, buffs.activeBuffs.size)
    assertEquals(def.id, buffs.activeBuffs.first().definitionId)
  }

  @Test
  fun `REFRESH_DURATION resets remaining duration instead of stacking`() {
    val buffs = Buffs()
    val def = definition(stackBehavior = StackBehavior.REFRESH_DURATION)

    buffs.applyBuff(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    buffs.activeBuffs.first().remainingSeconds = 2f
    buffs.applyBuff(def, level = 1, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)

    assertEquals(1, buffs.activeBuffs.size)
    assertEquals(10f, buffs.activeBuffs.first().remainingSeconds)
  }

  @Test
  fun `STACK_INDEPENDENT allows multiple instances`() {
    val buffs = Buffs()
    val def = definition(stackBehavior = StackBehavior.STACK_INDEPENDENT)

    buffs.applyBuff(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    buffs.applyBuff(def, level = 1, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)

    assertEquals(2, buffs.activeBuffs.size)
  }

  @Test
  fun `IGNORE_IF_PRESENT does not add a second instance`() {
    val buffs = Buffs()
    val def = definition(stackBehavior = StackBehavior.IGNORE_IF_PRESENT)

    buffs.applyBuff(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    buffs.applyBuff(def, level = 5, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)

    assertEquals(1, buffs.activeBuffs.size)
    assertEquals(1, buffs.activeBuffs.first().level)
  }

  @Test
  fun `REPLACE_IF_STRONGER only replaces when the new level is higher`() {
    val buffs = Buffs()
    val def = definition(stackBehavior = StackBehavior.REPLACE_IF_STRONGER)

    buffs.applyBuff(def, level = 3, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    buffs.applyBuff(def, level = 2, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)
    assertEquals(3, buffs.activeBuffs.first().level)

    buffs.applyBuff(def, level = 5, instanceId = 3L, sourceEntityId = null, durationSeconds = 10.0)
    assertEquals(1, buffs.activeBuffs.size)
    assertEquals(5, buffs.activeBuffs.first().level)
  }

  @Test
  fun `tickDown removes expired buffs`() {
    val buffs = Buffs()
    val def = definition()
    buffs.applyBuff(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 1.0)

    buffs.tickDown(0.5f)
    assertEquals(1, buffs.activeBuffs.size)

    buffs.tickDown(0.6f)
    assertTrue(buffs.activeBuffs.isEmpty())
  }

  @Test
  fun `consume removes a specific instance by id`() {
    val buffs = Buffs()
    val def = definition(stackBehavior = StackBehavior.STACK_INDEPENDENT)
    buffs.applyBuff(def, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    buffs.applyBuff(def, level = 1, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)

    val removed = buffs.consume(1L)

    assertTrue(removed)
    assertEquals(1, buffs.activeBuffs.size)
    assertEquals(2L, buffs.activeBuffs.first().instanceId)
  }

  @Test
  fun `toEntityMessage filters out buffs with showIcon false`() {
    val buffs = Buffs()
    val visible = definition(id = 1L, showIcon = true)
    val hidden = definition(id = 2L, showIcon = false)

    buffs.applyBuff(visible, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)
    buffs.applyBuff(hidden, level = 1, instanceId = 2L, sourceEntityId = null, durationSeconds = 10.0)

    val message = buffs.toEntityMessage(entityId = 42L) as BuffListSMSG

    assertEquals(42L, message.entityId)
    assertEquals(1, message.buffs.size)
    assertEquals(visible.id, message.buffs.first().buffId)
  }

  @Test
  fun `toEntityMessage marks debuffs`() {
    val buffs = Buffs()
    val debuff = definition(id = 1L, polarity = BuffPolarity.DEBUFF)

    buffs.applyBuff(debuff, level = 1, instanceId = 1L, sourceEntityId = null, durationSeconds = 10.0)

    val message = buffs.toEntityMessage(entityId = 1L) as BuffListSMSG
    assertTrue(message.buffs.first().debuff)
  }
}
