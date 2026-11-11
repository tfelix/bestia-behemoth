package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.casting.portal.WarpMarks
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TravelSageTest {

  private val world = testWorld()
  private val sage = world.createEntity { id -> add(id, Position.fromVec3(Vec3L(10, 20, 5))) }

  @Test
  fun `Teleport goes nearby at level 1 and home at level 2, and not while sick`() {
    val recording = RecordingSkillWorld()
    listOf(1, 2).forEach { Teleport().execute(SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), recording, skillLevel = it)) }
    assertEquals(listOf(false, true), recording.teleports)

    assertNull(Teleport().checkCastStart(world, sage, 1, emptyList()))
    world.add(sage, StatusEffects(mutableListOf(ActiveStatusEffect(StatusEffectId.TELEPORT_SICKNESS.id, 1, 300f))))
    assertEquals(OpError.TELEPORT_NOT_READY, Teleport().checkCastStart(world, sage, 1, emptyList()))
  }

  @Test
  fun `marking a spot is a free action that keeps one spot per level`() {
    val sut = WarpPortal()

    assertTrue(sut.freeAction(world, sage, skillLevel = 2, args = listOf(WarpPortal.MARK)))
    assertTrue(sut.freeAction(world, sage, skillLevel = 2, args = listOf(WarpPortal.MARK)))
    assertTrue(sut.freeAction(world, sage, skillLevel = 2, args = listOf(WarpPortal.MARK)))
    assertFalse(sut.freeAction(world, sage, skillLevel = 2, args = listOf(WarpPortal.HOME)))

    assertEquals(2, world.get(sage, WarpMarks::class)!!.spots.size)
  }

  @Test
  fun `a portal goes home or to a saved spot, and never to a spot that is not there`() {
    val sut = WarpPortal()
    sut.freeAction(world, sage, skillLevel = 1, args = listOf(WarpPortal.MARK))

    assertNull(sut.checkCastStart(world, sage, 1, listOf(WarpPortal.HOME)))
    assertNull(sut.checkCastStart(world, sage, 1, listOf(WarpPortal.FIRST_SPOT)))
    assertEquals(OpError.PORTAL_NO_SPOT, sut.checkCastStart(world, sage, 1, listOf(WarpPortal.FIRST_SPOT + 1)))
    assertEquals(OpError.SKILL_INVALID_CHOICE, sut.checkCastStart(world, sage, 1, emptyList()))
  }

  @Test
  fun `a portal lets two bodies through per level for 30 seconds`() {
    val recording = RecordingSkillWorld().apply { warpMarks = listOf(Vec3L(900, 900, 0)) }
    val ctx = SkillContextFixture.skillCtx(
      BattleContextFixture.groundCtx(), recording, skillLevel = 3, args = listOf(WarpPortal.FIRST_SPOT)
    )

    WarpPortal().execute(ctx)

    val portal = recording.portals.single()
    assertEquals(Vec3L(900, 900, 0), portal.to)
    assertEquals(6, portal.passages)
    assertEquals(30, portal.seconds)
  }
}
