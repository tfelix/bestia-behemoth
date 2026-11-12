package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AssassinShadowSkillsTest {

  private val world = RecordingSkillWorld()
  private val caster = BattleContextFixture.ATTACKER_ID

  @Test
  fun `Hide and Cloak put their own status on the caster, and a recast toggles it off`() {
    Hide().execute(ctx(level = 4))
    Cloak().execute(ctx(level = 2))

    assertEquals(
      listOf(Triple(caster, StatusEffectId.HIDING.id, 4), Triple(caster, StatusEffectId.CLOAKING.id, 2)),
      world.appliedEffects.map { Triple(it.targetEntityId, it.effectId, it.level) }
    )
    assertEquals(StatusEffectId.HIDING, Hide().toggledStatus)
    assertEquals(StatusEffectId.CLOAKING, Cloak().toggledStatus)
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
