package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnightGuardianSkillsTest {

  private val world = RecordingSkillWorld()
  private val caster = BattleContextFixture.ATTACKER_ID

  @Test
  fun `Shield Wall is a toggle that puts the wall up at the skill's level`() {
    world.shieldBearers += caster

    assertEquals(StatusEffectId.SHIELD_WALL, ShieldWall().toggledStatus)
    assertTrue(ShieldWall().isCastPossible(ctx(level = 4)))
    ShieldWall().execute(ctx(level = 4))

    assertEquals(
      listOf(RecordingSkillWorld.AppliedEffect(caster, StatusEffectId.SHIELD_WALL.id, 4)),
      world.appliedEffects
    )
  }

  @Test
  fun `Shield Wall needs a shield, at the button and at resolution`() {
    val ecs = testWorld()
    fun knightWith(shield: ArmorType?) = ecs.createEntity { id ->
      add(id, CombatStats(CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1)).copy(shieldArmorType = shield)))
    }

    assertEquals(OpError.SKILL_NEEDS_SHIELD, ShieldWall().checkCastStart(ecs, knightWith(null), 1, emptyList()))
    assertNull(ShieldWall().checkCastStart(ecs, knightWith(ArmorType.HEAVY), 1, emptyList()))
    assertFalse(ShieldWall().isCastPossible(ctx(level = 1)), "no shield at resolution")
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
