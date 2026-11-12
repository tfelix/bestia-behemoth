package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusValues
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnightGuardianSkillsTest {

  private val world = RecordingSkillWorld()
  private val caster = BattleContextFixture.ATTACKER_ID
  private val target = BattleContextFixture.DEFENDER_ID

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

  @Test
  fun `Devotion puts a fresh devotion on a party member, so the newest Knight wins`() {
    world.partyMembers += target

    assertTrue(Devotion(LineOfSightService()).isCastPossible(ctx(level = 2)))
    Devotion(LineOfSightService()).execute(ctx(level = 2))

    assertEquals(listOf(target to StatusEffectId.DEVOTED.id), world.removedEffects)
    assertEquals(listOf(RecordingSkillWorld.AppliedEffect(target, StatusEffectId.DEVOTED.id, 2)), world.appliedEffects)
  }

  @Test
  fun `Devotion fizzles on someone outside the party`() {
    assertFalse(Devotion(LineOfSightService()).isCastPossible(ctx(level = 1)))
  }

  @Test
  fun `Devotion is refused at the button on the Knight and outside the party`() {
    val ecs = testWorld()
    val knight = ecs.createEntity { id -> add(id, Master(masterId = 1L)) }
    val ownBestia = ecs.createEntity { id -> add(id, OwnedBestia(masterId = 1L, playerBestiaId = 5L)) }
    val stranger = ecs.createEntity { id -> add(id, Master(masterId = 2L)) }
    val devotion = Devotion(LineOfSightService())

    assertNull(devotion.checkTarget(ecs, knight, ownBestia, 1, emptyList()))
    assertEquals(OpError.SKILL_NEEDS_PARTY_MEMBER, devotion.checkTarget(ecs, knight, knight, 1, emptyList()))
    assertEquals(OpError.SKILL_NEEDS_PARTY_MEMBER, devotion.checkTarget(ecs, knight, stranger, 1, emptyList()))
  }

  @Test
  fun `Juggernaut takes on the stance at the skill's level and cools down faster at Lv 3`() {
    Juggernaut().execute(ctx(level = 3))

    assertEquals(listOf(RecordingSkillWorld.AppliedEffect(caster, StatusEffectId.JUGGERNAUT.id, 3)), world.appliedEffects)
    assertEquals(listOf(120f, 120f, 90f), (1..3).map { Juggernaut.cooldownSeconds(it) })
  }

  private fun ctx(level: Int) = SkillContextFixture.skillCtx(world = world, skillLevel = level)
}
