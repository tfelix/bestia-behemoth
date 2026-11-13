package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.AttackType
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
import net.bestia.zone.geometry.Vec3L
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

  @Test
  fun `Discord Strike hits a foe with a sound wave for 150 plus 30 percent ATK a level`() {
    world.foes += target
    world.heldWeapons[caster] = WeaponType.INSTRUMENT
    val discord = DiscordStrike(LineOfSightService())

    assertTrue(discord.isCastPossible(ctx(level = 5)))
    discord.execute(ctx(level = 5))

    assertEquals(listOf(target to 300), world.strikes)
    assertEquals(AttackType.RANGED_PHYSICAL, world.strikeAttacks.single().attackType, "a shot, not a swing")
    assertEquals(listOf(180, 210, 240, 270, 300), (1..5).map { DiscordStrike.atkPercent(it) })
  }

  @Test
  fun `Discord Strike needs an instrument and a foe`() {
    val discord = DiscordStrike(LineOfSightService())

    assertEquals(OpError.SKILL_NEEDS_INSTRUMENT, discord.checkCastStart(ecs, bardWith(WeaponType.WHIP), 1, emptyList()))
    assertNull(discord.checkCastStart(ecs, bardWith(WeaponType.INSTRUMENT), 1, emptyList()))

    world.foes += target
    world.heldWeapons[caster] = WeaponType.WHIP
    assertFalse(discord.isCastPossible(ctx(level = 1)), "a whip is no instrument")
    world.heldWeapons[caster] = WeaponType.INSTRUMENT
    world.foes -= target
    assertFalse(discord.isCastPossible(ctx(level = 1)), "not at a friend")
  }

  @Test
  fun `Dissonant Chord puts Dissonance at its level on every foe around the Bard`() {
    val wolf = 7L
    val ally = 8L
    val around = RecordingSkillWorld(entitiesNearby = setOf(caster, target, wolf, ally)).putPosition(caster, Vec3L(10, 10, 0))
    around.foes += setOf(target, wolf)
    around.heldWeapons[caster] = WeaponType.INSTRUMENT
    val ctx = SkillContextFixture.skillCtx(world = around, skillLevel = 4)

    assertTrue(DissonantChord().isCastPossible(ctx))
    DissonantChord().execute(ctx)

    assertEquals(setOf(target, wolf), around.appliedEffects.map { it.targetEntityId }.toSet())
    assertTrue(around.appliedEffects.all { it.effectId == StatusEffectId.DISSONANCE.id && it.level == 4 })
  }

  @Test
  fun `Dissonant Chord needs an instrument`() {
    assertEquals(OpError.SKILL_NEEDS_INSTRUMENT, DissonantChord().checkCastStart(ecs, bardWith(null), 1, emptyList()))
    assertNull(DissonantChord().checkCastStart(ecs, bardWith(WeaponType.INSTRUMENT), 1, emptyList()))
    assertFalse(DissonantChord().isCastPossible(ctx(level = 1)), "nothing held at resolution")
  }

  private fun bardWith(weapon: WeaponType?): EntityId {
    return ecs.createEntity { id ->
      add(id, CombatStats(CombatValues.ofBase(1, StatusValues(1, 1, 1, 1, 1, 1)).copy(heldWeaponType = weapon)))
    }
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
