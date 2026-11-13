package net.bestia.zone.casting.scripts.master

import io.mockk.every
import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.ammo.Ammunition
import net.bestia.zone.battle.attack.AttackType
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HunterTrackerSkillsTest {

  private val world = RecordingSkillWorld()
  private val caster = BattleContextFixture.ATTACKER_ID
  private val target = BattleContextFixture.DEFENDER_ID

  @Test
  fun `Hunter's Mark marks a foe as prey at the cast's level`() {
    world.foes += target
    val mark = HuntersMark(LineOfSightService())

    assertTrue(mark.isCastPossible(ctx(level = 4)))
    mark.execute(ctx(level = 4))

    assertEquals(listOf(target to 4), world.markedPrey)
  }

  @Test
  fun `Hunter's Mark is not cast on a friend`() {
    assertFalse(HuntersMark(LineOfSightService()).isCastPossible(ctx(level = 1)))
  }

  @Test
  fun `Javelin hurls the spear or thrown weapon in hand as a shot for 150 plus 30 percent ATK a level`() {
    world.foes += target
    world.heldWeapons[caster] = WeaponType.SPEAR
    world.throwable += Ammunition(itemId = SPEAR_ITEM, attack = 0, element = Element.NORMAL)
    val javelin = Javelin(LineOfSightService(), mockk())

    assertTrue(javelin.isCastPossible(ctx(level = 5)))
    javelin.execute(ctx(level = 5))

    assertEquals(listOf(target to 300), world.strikes)
    assertEquals(AttackType.RANGED_PHYSICAL, world.strikeAttacks.single().attackType)
    assertEquals(SPEAR_ITEM, world.strikeAttacks.single().ammunition?.itemId, "the client draws it flying")
  }

  @Test
  fun `Javelin with nothing left to throw tells the caster and strikes nothing`() {
    world.foes += target
    world.heldWeapons[caster] = WeaponType.THROWN

    Javelin(LineOfSightService(), mockk()).execute(ctx(level = 1))

    assertEquals(emptyList<Pair<Long, Int>>(), world.strikes)
    assertEquals(listOf(OpError.SKILL_NOTHING_TO_THROW), world.toldCaster)
  }

  @Test
  fun `Javelin fizzles without a spear or a thrown weapon in hand`() {
    world.foes += target
    world.heldWeapons[caster] = WeaponType.BOW

    assertFalse(Javelin(LineOfSightService(), mockk()).isCastPossible(ctx(level = 1)))
  }

  @Test
  fun `Javelin is refused at the button without a spear or a thrown weapon, or with no javelin left to throw`() {
    val ammo = mockk<AmmoSupply>()
    val javelin = Javelin(LineOfSightService(), ammo)
    val ecs = testWorld()
    fun hunterWith(weapon: WeaponType?) = ecs.createEntity { id ->
      add(id, CombatStats(CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1)).copy(heldWeaponType = weapon)))
    }
    val withSpear = hunterWith(WeaponType.SPEAR)
    val withJavelins = hunterWith(WeaponType.THROWN)
    val withLastJavelin = hunterWith(WeaponType.THROWN)
    every { ammo.hasThrownSpare(ecs, withJavelins) } returns true
    every { ammo.hasThrownSpare(ecs, withLastJavelin) } returns false

    assertNull(javelin.checkCastStart(ecs, withSpear, 1))
    assertNull(javelin.checkCastStart(ecs, withJavelins, 1))
    assertEquals(OpError.SKILL_NOTHING_TO_THROW, javelin.checkCastStart(ecs, withLastJavelin, 1))
    assertEquals(OpError.SKILL_NEEDS_SPEAR_OR_THROWN, javelin.checkCastStart(ecs, hunterWith(WeaponType.SWORD), 1))
  }

  private fun ctx(level: Int): SkillContext {
    return SkillContextFixture.skillCtx(world = world, skillLevel = level)
  }

  private companion object {
    const val SPEAR_ITEM = 307L
  }
}
