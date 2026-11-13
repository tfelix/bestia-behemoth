package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BardFighterSkillsTest {

  private val caster = BattleContextFixture.ATTACKER_ID
  private val target = BattleContextFixture.DEFENDER_ID
  private val world = RecordingSkillWorld(entitiesNearby = setOf(caster, target))
  private val ecs = testWorld()

  @Test
  fun `Hobbling Lash strikes a foe and leaves it Slowed by 20 percent for 3 s plus 1 s a level`() {
    world.foes += target
    world.heldWeapons[caster] = WeaponType.WHIP
    world.strikeResult = HitDamage(30)
    val lash = HobblingLash(LineOfSightService())

    assertTrue(lash.isCastPossible(ctx(level = 5)))
    lash.execute(ctx(level = 5))

    assertEquals(listOf(target to 220), world.strikes)
    val slowed = world.appliedEffects.single()
    assertEquals(target, slowed.targetEntityId)
    assertEquals(StatusEffectId.SLOWED.id, slowed.effectId)
    assertEquals(2, slowed.level)
    assertEquals(8.0, slowed.durationSeconds)
    assertEquals(listOf(140, 160, 180, 200, 220), (1..5).map { HobblingLash.atkPercent(it) })
    assertEquals(listOf(4.0, 5.0, 6.0, 7.0, 8.0), (1..5).map { HobblingLash.slowedSeconds(it) })
  }

  @Test
  fun `a Hobbling Lash that misses slows nothing`() {
    world.foes += target
    world.strikeResult = Miss

    HobblingLash(LineOfSightService()).execute(ctx(level = 1))

    assertTrue(world.appliedEffects.isEmpty())
  }

  @Test
  fun `Hobbling Lash needs a whip and a foe`() {
    val lash = HobblingLash(LineOfSightService())

    assertEquals(OpError.SKILL_NEEDS_WHIP, lash.checkCastStart(ecs, bardWith(WeaponType.INSTRUMENT), 1, emptyList()))
    assertEquals(OpError.SKILL_NEEDS_WHIP, lash.checkCastStart(ecs, bardWith(null), 1, emptyList()))
    assertNull(lash.checkCastStart(ecs, bardWith(WeaponType.WHIP), 1, emptyList()))

    world.foes += target
    assertFalse(lash.isCastPossible(ctx(level = 1)), "nothing held at resolution")
    world.heldWeapons[caster] = WeaponType.WHIP
    world.foes -= target
    assertFalse(lash.isCastPossible(ctx(level = 1)), "not at a friend")
  }

  private fun bardWith(weapon: WeaponType?): EntityId {
    return ecs.createEntity { id ->
      add(id, CombatStats(CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1)).copy(heldWeaponType = weapon)))
    }
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
