package net.bestia.zone.casting.scripts.utility

import net.bestia.zone.battle.BattleContextFixture.DEFENDER_ID
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ApplyStatusTest {

  @Test
  fun `puts its row's status on the friend it was cast at`() {
    val world = RecordingSkillWorld()
    val skill = SkillContextFixture.skill().apply { statusEffect = StatusEffectId.WARMED.name }

    ApplyStatus().execute(SkillContextFixture.skillCtx(world = world, skill = skill))

    assertEquals(
      RecordingSkillWorld.AppliedEffect(DEFENDER_ID, StatusEffectId.WARMED.id, level = 1),
      world.appliedEffects.single()
    )
  }

  @Test
  fun `a row with no status cannot be cast`() {
    assertFalse(ApplyStatus().isCastPossible(SkillContextFixture.skillCtx()))
  }
}
