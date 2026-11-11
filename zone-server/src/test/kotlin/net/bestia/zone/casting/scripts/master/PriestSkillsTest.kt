package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.Size
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.casting.scripts.Blessing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PriestSkillsTest {

  private val world = RecordingSkillWorld()
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `Signum Crucis and Lex Aeterna mark their target at the cast level`() {
    SignumCrucis(LineOfSightService()).execute(ctx(level = 2))
    LexAeterna(LineOfSightService()).execute(ctx(level = 1))

    assertEquals(
      listOf(StatusEffectId.SIGNUM_CRUCIS.id to 2, StatusEffectId.LEX_AETERNA.id to 1),
      world.appliedEffects.map { it.effectId to it.level }
    )
    assertEquals(setOf(target), world.appliedEffects.map { it.targetEntityId }.toSet())
  }

  @Test
  fun `Blessing curses an undead only when it is a foe`() {
    world.natures[target] = Nature(Element.UNDEAD, Size.MEDIUM)

    Blessing(LineOfSightService()).execute(ctx(level = 1))
    assertEquals(0, world.appliedEffects.size)

    world.foes += target
    Blessing(LineOfSightService()).execute(ctx(level = 1))
    assertEquals(listOf(StatusEffectId.BLESSING.id), world.appliedEffects.map { it.effectId })
  }

  @Test
  fun `Status Recovery lifts Blind, Confused and Feared and nothing else`() {
    world.effectsOn[target] = mutableSetOf(
      StatusEffectId.BLIND.id, StatusEffectId.CONFUSED.id, StatusEffectId.FEARED.id, StatusEffectId.POISON.id
    )

    StatusRecovery(LineOfSightService()).execute(ctx(level = 1))

    assertEquals(setOf(StatusEffectId.POISON.id), world.effectsOn[target])
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world, skillLevel = level)
}
