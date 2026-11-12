package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnightVanguardSkillsTest {

  private val world = RecordingSkillWorld()
  private val caster = BattleContextFixture.ATTACKER_ID
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `Provoke puts a fresh provoke on a foe, so the newest provoker wins`() {
    val provoke = Provoke(LineOfSightService())
    world.foes += target

    assertTrue(provoke.isCastPossible(ctx(level = 3)))
    provoke.execute(ctx(level = 3))

    assertEquals(listOf(target to StatusEffectId.PROVOKED.id), world.removedEffects)
    assertEquals(
      listOf(Triple(target, StatusEffectId.PROVOKED.id, 3)),
      world.appliedEffects.map { Triple(it.targetEntityId, it.effectId, it.level) }
    )
  }

  @Test
  fun `Provoke fizzles on a friend and leaves a master alone`() {
    val provoke = Provoke(LineOfSightService())
    assertFalse(provoke.isCastPossible(ctx(level = 1)), "not a foe")

    world.putMaster(target, masterId = 9L)
    provoke.execute(ctx(level = 1))

    assertTrue(world.appliedEffects.isEmpty())
  }

  @Test
  fun `Provoke is refused at the button on a master`() {
    val ecs = testWorld()
    val master = ecs.createEntity { id -> add(id, Master(masterId = 9L)) }
    val wolf = ecs.createEntity { }
    val provoke = Provoke(LineOfSightService())

    assertEquals(OpError.SKILL_NOT_ON_MASTERS, provoke.checkTarget(ecs, caster, master, 1, emptyList()))
    assertNull(provoke.checkTarget(ecs, caster, wolf, 1, emptyList()))
  }

  @Test
  fun `Bash strikes at 120 to 200 percent ATK and adds its stun chance to the gear's`() {
    world.strikeResult = HitDamage(40)
    world.gearStunChances[caster] = 5

    Bash(LineOfSightService()).execute(ctx(level = 5))

    assertEquals(listOf(target to 200), world.strikes)
    assertEquals(
      listOf(RecordingSkillWorld.Inflicted(target, "STUN", chancePercent = 30, durationSeconds = 1.0, level = 1)),
      world.inflicted
    )
    assertEquals(listOf(120, 140, 160, 180, 200), (1..5).map { Bash.atkPercent(it) })
  }

  @Test
  fun `a Bash that misses stuns nothing`() {
    world.strikeResult = Miss

    Bash(LineOfSightService()).execute(ctx(level = 5))

    assertTrue(world.inflicted.isEmpty())
  }

  @Test
  fun `Brandish Spear strikes every foe in front of the Knight at 120 percent ATK and 40 more each level`() {
    val wolf = 7L
    val ally = 8L
    val world = RecordingSkillWorld(entitiesNearby = setOf(target, wolf, ally))
    world.foes += setOf(target, wolf)
    world.heldWeapons[caster] = WeaponType.POLEARM
    val ctx = SkillContextFixture.skillCtx(world = world, skillLevel = 5)

    assertTrue(BrandishSpear(LineOfSightService()).isCastPossible(ctx))
    BrandishSpear(LineOfSightService()).execute(ctx)

    assertEquals(setOf(target to 280, wolf to 280), world.strikes.toSet())
    assertEquals(listOf(120, 160, 200, 240, 280), (1..5).map { BrandishSpear.atkPercent(it) })
  }

  @Test
  fun `Brandish Spear covers the 3x3 tiles that start next to the Knight, straight or diagonal`() {
    val knight = Vec3L(10, 10, 0)

    assertEquals(Vec3L(12, 10, 0), BrandishSpear.frontArea(knight, aimedAt = Vec3L(11, 10, 0)))
    assertEquals(Vec3L(8, 12, 0), BrandishSpear.frontArea(knight, aimedAt = Vec3L(9, 11, 0)))
  }

  @Test
  fun `Brandish Spear needs a spear or a polearm`() {
    val ecs = testWorld()
    fun knightWith(weapon: WeaponType?) = ecs.createEntity { id ->
      add(id, CombatStats(CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1)).copy(heldWeaponType = weapon)))
    }
    val brandish = BrandishSpear(LineOfSightService())

    assertEquals(OpError.SKILL_NEEDS_SPEAR, brandish.checkCastStart(ecs, knightWith(WeaponType.SWORD), 1, emptyList()))
    assertEquals(OpError.SKILL_NEEDS_SPEAR, brandish.checkCastStart(ecs, knightWith(null), 1, emptyList()))
    assertNull(brandish.checkCastStart(ecs, knightWith(WeaponType.SPEAR), 1, emptyList()))
    assertFalse(brandish.isCastPossible(ctx(level = 1)), "nothing held at resolution")
  }

  @Test
  fun `Charge dashes next to the foe, strikes at 150 percent ATK and provokes it for 3 seconds`() {
    world.foes += target
    // The recording world does not move anybody: the Knight stands where the dash would have put it.
    world.putPosition(target, Vec3L(8, 0, 0)).putPosition(caster, Vec3L(7, 1, 0))

    assertTrue(Charge(LineOfSightService()).isCastPossible(ctx(level = 2)))
    Charge(LineOfSightService()).execute(ctx(level = 2))

    assertEquals(listOf(RecordingSkillWorld.ForcedMove(caster, Vec3L(8, 0, 0), 5, keepDistance = 1)), world.forcedMoves)
    assertEquals(listOf(target to 150), world.strikes)
    assertEquals(
      listOf(Triple(StatusEffectId.PROVOKED.id, 1, 3.0)),
      world.appliedEffects.map { Triple(it.effectId, it.level, it.durationSeconds) }
    )
  }

  @Test
  fun `a Charge stopped short strikes nothing`() {
    world.foes += target
    world.putPosition(target, Vec3L(8, 0, 0)).putPosition(caster, Vec3L(4, 0, 0))

    Charge(LineOfSightService()).execute(ctx(level = 1))

    assertTrue(world.strikes.isEmpty())
    assertTrue(world.appliedEffects.isEmpty())
  }

  @Test
  fun `Charge cools down half a second faster each level`() {
    assertEquals(listOf(14.5f, 14f, 13.5f, 13f, 12.5f), (1..5).map { Charge.cooldownSeconds(it) })
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
