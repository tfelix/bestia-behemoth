package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.skill.RecordingSkillWorld
import net.bestia.zone.battle.skill.SkillContextFixture
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayDeadTest {

  private val sut = PlayDead()

  @Test
  fun `feigning death applies the effect that hides the caster from perception`() {
    val world = RecordingSkillWorld()

    sut.execute(SkillContextFixture.skillCtx(world = world))

    val applied = world.appliedEffects.single()
    assertEquals(StatusEffectId.PLAY_DEAD.id, applied.effectId)
    assertEquals(
      BattleContextFixture.ATTACKER_ID,
      applied.targetEntityId,
      "Play Dead is cast on oneself whatever happened to be selected"
    )
  }

  @Test
  fun `cast on oneself, so neither range nor line of sight can refuse it`() {
    assertTrue(sut.isCastPossible(SkillContextFixture.skillCtx()))
    assertTrue(
      sut.isCastPossible(
        SkillContextFixture.skillCtx(BattleContextFixture.groundCtx(targetPosition = Vec3L(900, 0, 0)))
      )
    )
  }
}
