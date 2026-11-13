package net.bestia.zone.casting.scripts.master

import io.mockk.every
import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.RangedWeapon
import net.bestia.zone.battle.Weapon
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.ammo.AmmoType
import net.bestia.zone.battle.ammo.Ammunition
import net.bestia.zone.battle.attack.AttackType
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HunterArcherSkillsTest {

  private val caster = BattleContextFixture.ATTACKER_ID
  private val target = BattleContextFixture.DEFENDER_ID
  private val other = 3L
  private val world = RecordingSkillWorld(entitiesNearby = setOf(caster, target, other))

  @Test
  fun `Double Strafe shoots two arrows for 100 plus 10 percent ATK a level, from one arrow`() {
    archerWithArrow()
    val strafe = DoubleStrafe(LineOfSightService(), mockk())

    assertTrue(strafe.isCastPossible(ctx(level = 10)))
    strafe.execute(ctx(level = 10))

    assertEquals(listOf(target to 200, target to 200), world.strikes)
    assertTrue(world.strikeAttacks.all { it.attackType == AttackType.RANGED_PHYSICAL && it.ammunition == FIRE_ARROW })
    assertTrue(world.strikeAttacks.all { it.attackElement == Element.FIRE }, "the arrow decides the element")
  }

  @Test
  fun `a bow skill with the quiver empty tells the caster and shoots nothing`() {
    world.foes += target
    world.heldWeapons[caster] = WeaponType.BOW

    DoubleStrafe(LineOfSightService(), mockk()).execute(ctx(level = 1))

    assertEquals(emptyList<Pair<Long, Int>>(), world.strikes)
    assertEquals(listOf(OpError.ATTACK_OUT_OF_AMMO), world.toldCaster)
  }

  @Test
  fun `a bow skill fizzles without a bow in hand`() {
    world.foes += target
    world.heldWeapons[caster] = WeaponType.CROSSBOW

    assertFalse(DoubleStrafe(LineOfSightService(), mockk()).isCastPossible(ctx(level = 1)))
    assertFalse(ChargeArrow(LineOfSightService(), mockk()).isCastPossible(ctx(level = 1)))
  }

  @Test
  fun `a bow skill is not shot at a friend`() {
    world.heldWeapons[caster] = WeaponType.BOW

    assertFalse(DoubleStrafe(LineOfSightService(), mockk()).isCastPossible(ctx(level = 1)))
  }

  @Test
  fun `Arrow Shower hits every foe in the 3x3 for 80 plus 10 percent ATK a level and pushes the hit ones back`() {
    archerWithArrow()
    world.foes += other
    world.strikeResult = HitDamage(10)
    val ground = SkillContextFixture.skillCtx(BattleContextFixture.groundCtx(), world, skillLevel = 5)
    val shower = ArrowShower(LineOfSightService(), mockk())

    assertTrue(shower.isCastPossible(ground))
    shower.execute(ground)

    assertEquals(setOf(target to 130, other to 130), world.strikes.toSet(), "not the Hunter itself")
    assertEquals(
      setOf(RecordingSkillWorld.KnockBack(target, HUNTER_AT, 1), RecordingSkillWorld.KnockBack(other, HUNTER_AT, 1)),
      world.knockBacks.toSet()
    )
  }

  @Test
  fun `Charge Arrow shoots once for 150 percent ATK and pushes the foe 6 m away`() {
    archerWithArrow()
    world.strikeResult = HitDamage(10)

    ChargeArrow(LineOfSightService(), mockk()).execute(ctx(level = 1))

    assertEquals(listOf(target to 150), world.strikes)
    assertEquals(listOf(RecordingSkillWorld.KnockBack(target, HUNTER_AT, 6)), world.knockBacks)
  }

  @Test
  fun `a Charge Arrow that misses pushes nobody`() {
    archerWithArrow()
    world.strikeResult = Miss

    ChargeArrow(LineOfSightService(), mockk()).execute(ctx(level = 1))

    assertEquals(emptyList<RecordingSkillWorld.KnockBack>(), world.knockBacks)
  }

  @Test
  fun `a bow skill reaches as far as the bow in hand`() {
    val ecs = testWorld()
    val archer = ecs.createEntity { id -> add(id, CombatStats(valuesWith(WeaponType.BOW, BOW))) }
    val cost = SkillLevelCost(manaCost = 12, staminaCost = 0, cooldownSeconds = 0f, castTimeSeconds = 0f, rangeTiles = 1)

    assertEquals(BOW.range, DoubleStrafe(LineOfSightService(), mockk()).reachOf(ecs, archer, cost))
  }

  @Test
  fun `a bow skill is refused at the button without a bow, or without an arrow for it`() {
    val ammo = mockk<AmmoSupply>()
    val strafe = DoubleStrafe(LineOfSightService(), ammo)
    val ecs = testWorld()
    val loaded = ecs.createEntity { id -> add(id, CombatStats(valuesWith(WeaponType.BOW, BOW))) }
    val empty = ecs.createEntity { id -> add(id, CombatStats(valuesWith(WeaponType.BOW, BOW))) }
    val crossbow = ecs.createEntity { id -> add(id, CombatStats(valuesWith(WeaponType.CROSSBOW, BOW))) }
    every { ammo.nextFor(ecs, loaded, BOW) } returns FIRE_ARROW
    every { ammo.nextFor(ecs, empty, BOW) } returns null

    assertNull(strafe.checkCastStart(ecs, loaded, 1))
    assertEquals(OpError.ATTACK_OUT_OF_AMMO, strafe.checkCastStart(ecs, empty, 1))
    assertEquals(OpError.SKILL_NEEDS_BOW, strafe.checkCastStart(ecs, crossbow, 1))
  }

  @Test
  fun `Improve Concentration sharpens the Hunter and ends the hiding within 3 m once`() {
    ImproveConcentration().execute(ctx(level = 4))

    assertEquals(listOf(3L), world.reveals)
    assertEquals(
      listOf(caster to 4),
      world.appliedEffects.filter { it.effectId == StatusEffectId.IMPROVE_CONCENTRATION.id }.map { it.targetEntityId to it.level }
    )
  }

  private fun archerWithArrow() {
    world.foes += target
    world.heldWeapons[caster] = WeaponType.BOW
    world.ammo = FIRE_ARROW
  }

  private fun valuesWith(held: WeaponType, ranged: RangedWeapon): CombatValues {
    val base = CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1))

    return base.copy(heldWeaponType = held, weapon = Weapon(atk = 22, upgradeLevel = 0, matk = 0, ranged = ranged))
  }

  private fun ctx(level: Int): SkillContext {
    return SkillContextFixture.skillCtx(world = world, skillLevel = level)
  }

  private companion object {
    val BOW = RangedWeapon(range = 12, ammoType = AmmoType.ARROW)
    val FIRE_ARROW = Ammunition(itemId = 96, attack = 3, element = Element.FIRE)
    val HUNTER_AT = Vec3L(1, 0, 0)
  }
}
