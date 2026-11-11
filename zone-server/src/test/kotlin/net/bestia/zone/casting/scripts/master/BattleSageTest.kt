package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BattleSageTest {

  @Test
  fun `Dispel tries buffs and curses at 50 + 10 per level`() {
    val world = RecordingSkillWorld()
    val ctx = SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world, skillLevel = 3)

    MasterDispel(LineOfSightService()).execute(ctx)

    assertEquals(80, world.fullDispels.single().second)
  }

  @Test
  fun `Endow takes only fire, water, wind or earth and stores the element on the ally`() {
    val sut = Endow(LineOfSightService())

    assertEquals(OpError.SKILL_INVALID_CHOICE, sut.checkCastStart(testWorld(), 1L, 1, listOf(Element.HOLY.ordinal.toLong())))
    assertEquals(OpError.SKILL_INVALID_CHOICE, sut.checkCastStart(testWorld(), 1L, 1, emptyList()))
    assertNull(sut.checkCastStart(testWorld(), 1L, 1, listOf(Element.FIRE.ordinal.toLong())))

    val world = RecordingSkillWorld()
    val ctx = SkillContextFixture.skillCtx(
      BattleContextFixture.entityCtx(), world, skillLevel = 2, args = listOf(Element.WIND.ordinal.toLong())
    )
    sut.execute(ctx)

    val applied = world.appliedEffects.single()
    assertEquals(StatusEffectId.MASTER_ENDOWED.id, applied.effectId)
    assertEquals(Element.WIND.ordinal, applied.amount)
    assertEquals(2, applied.level)
  }

  @Test
  fun `Magic Rod and Mana Drain put their status on the caster`() {
    val world = RecordingSkillWorld()
    val ctx = SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world, skillLevel = 2)

    MagicRod().execute(ctx)
    ManaDrain().execute(ctx)

    assertEquals(
      listOf(StatusEffectId.MAGIC_ROD.id, StatusEffectId.MANA_DRAINING.id),
      world.appliedEffects.map { it.effectId }
    )
    assertTrue(world.appliedEffects.all { it.targetEntityId == ctx.casterId })
  }

  @Test
  fun `Land Protector lays a zone that blocks ground spells, wider and longer per level`() {
    val world = RecordingSkillWorld()
    val ctx = SkillContextFixture.skillCtx(BattleContextFixture.groundCtx(), world, skillLevel = 3)

    LandProtector().execute(ctx)

    val zone = world.spawnedAreaEffects.single().effect
    assertTrue(zone.mechanics.blocksGroundSpells)
    assertEquals(5L, zone.radiusTiles)
    assertEquals(0, zone.damagePerTick)
    assertEquals(35, zone.remainingTicks)
  }
}
