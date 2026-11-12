package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

  @Test
  fun `Staff Sweep strikes every foe around the Brawler and pushes back those it hits`() {
    val wolf = 7L
    val ally = 8L
    val world = RecordingSkillWorld(entitiesNearby = setOf(caster, target, wolf, ally))
      .putPosition(caster, Vec3L(10, 10, 0))
    world.foes += setOf(target, wolf)
    world.heldWeapons[caster] = WeaponType.STAFF
    world.strikeResult = HitDamage(40)
    val ctx = SkillContextFixture.skillCtx(world = world, skillLevel = 5)

    assertTrue(StaffSweep().isCastPossible(ctx))
    StaffSweep().execute(ctx)

    assertEquals(setOf(target to 200, wolf to 200), world.strikes.toSet())
    assertEquals(
      setOf(RecordingSkillWorld.KnockBack(target, Vec3L(10, 10, 0), 1), RecordingSkillWorld.KnockBack(wolf, Vec3L(10, 10, 0), 1)),
      world.knockBacks.toSet()
    )
    assertEquals(listOf(120, 140, 160, 180, 200), (1..5).map { StaffSweep.atkPercent(it) })
  }

  @Test
  fun `a Staff Sweep that misses pushes nobody`() {
    val world = RecordingSkillWorld(entitiesNearby = setOf(target)).putPosition(caster, Vec3L(0, 0, 0))
    world.foes += target
    world.strikeResult = Miss

    StaffSweep().execute(SkillContextFixture.skillCtx(world = world, skillLevel = 1))

    assertTrue(world.knockBacks.isEmpty())
  }

  @Test
  fun `Staff Sweep needs a staff`() {
    val ecs = testWorld()
    fun brawlerWith(weapon: WeaponType?) = ecs.createEntity { id ->
      add(id, CombatStats(CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1)).copy(heldWeaponType = weapon)))
    }

    assertEquals(OpError.SKILL_NEEDS_STAFF, StaffSweep().checkCastStart(ecs, brawlerWith(WeaponType.FIST), 1, emptyList()))
    assertEquals(OpError.SKILL_NEEDS_STAFF, StaffSweep().checkCastStart(ecs, brawlerWith(null), 1, emptyList()))
    assertNull(StaffSweep().checkCastStart(ecs, brawlerWith(WeaponType.STAFF), 1, emptyList()))
    assertFalse(StaffSweep().isCastPossible(ctx(level = 1)), "nothing held at resolution")
  }

  @Test
  fun `Rush dashes next to the foe and lands one basic blow`() {
    world.foes += target
    // The recording world does not move anybody: the Brawler stands where the dash would have put it.
    world.putPosition(target, Vec3L(6, 0, 0)).putPosition(caster, Vec3L(5, 0, 0))

    assertTrue(Rush(LineOfSightService()).isCastPossible(ctx(level = 1)))
    Rush(LineOfSightService()).execute(ctx(level = 1))

    assertEquals(listOf(RecordingSkillWorld.ForcedMove(caster, Vec3L(6, 0, 0), 5, keepDistance = 1)), world.forcedMoves)
    assertEquals(listOf(target to 100), world.strikes)
  }

  @Test
  fun `a Rush stopped short strikes nothing`() {
    world.foes += target
    world.putPosition(target, Vec3L(6, 0, 0)).putPosition(caster, Vec3L(2, 0, 0))

    Rush(LineOfSightService()).execute(ctx(level = 1))

    assertTrue(world.strikes.isEmpty())
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
