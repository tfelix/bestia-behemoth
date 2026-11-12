package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrawlerEnduranceSkillsTest {

  private val world = RecordingSkillWorld()
  private val caster = BattleContextFixture.ATTACKER_ID
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `Second Wind gives back half the stamina, and from Lv 2 a fifth of the HP`() {
    SecondWind().execute(ctx(level = 1))
    SecondWind().execute(ctx(level = 2))

    assertEquals(listOf(50 to 0, 50 to 20), world.casterRestorations)
  }

  @Test
  fun `Second Wind cools down 180 seconds, 120 at Lv 3`() {
    assertEquals(listOf(180f, 180f, 120f), (1..3).map { SecondWind.cooldownSeconds(it) })
  }

  @Test
  fun `Battle Fury goes Berserk for 10 seconds a level`() {
    BattleFury().execute(ctx(level = 3))

    assertEquals(
      listOf(Triple(caster, StatusEffectId.BERSERK.id, 30.0)),
      world.appliedEffects.map { Triple(it.targetEntityId, it.effectId, it.durationSeconds) }
    )
    assertEquals(listOf(10.0, 20.0, 30.0), (1..3).map { BattleFury.durationSeconds(it) })
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
