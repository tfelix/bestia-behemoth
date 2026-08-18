package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.damage.Buff
import net.bestia.zone.battle.status.StatusEffectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayDeadTest {

  private val sut = PlayDead()

  @Test
  fun `feigning death applies the effect that hides the caster from perception`() {
    val result = sut.doAttack(BattleContextFixture.entityCtx())

    assertEquals(Buff(StatusEffectId.PLAY_DEAD.id), result)
  }

  @Test
  fun `cast on oneself, so neither range nor line of sight can refuse it`() {
    assertTrue(sut.isAttackPossible(BattleContextFixture.entityCtx()))
    assertTrue(
      sut.isAttackPossible(BattleContextFixture.groundCtx(targetPosition = net.bestia.zone.geometry.Vec3L(900, 0, 0)))
    )
  }
}
