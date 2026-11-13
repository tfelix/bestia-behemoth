package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.casting.RecordingSkillWorld
import net.bestia.zone.casting.SkillContextFixture
import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlastingTest {

  private val spot = Vec3L(3, 0, 0)

  @Test
  fun `each level blasts a metre wider, from 2 m`() {
    val radii = (1..5).map { level ->
      val world = RecordingSkillWorld()
      Blasting().execute(ctx(world, level))
      world.blastCharges.single()
    }

    assertEquals((2..6).map { spot to it }, radii)
  }

  @Test
  fun `the blast hurts foes once, as the fuse burns down`() {
    val world = RecordingSkillWorld()

    Blasting().execute(ctx(world, level = 3))

    val area = world.spawnedAreaEffects.single()
    assertEquals(spot, area.centre)
    assertEquals(Blasting.VISUAL_ID, area.visualId)
    assertEquals(4L, area.effect.radiusTiles)
    assertEquals(1, area.effect.remainingTicks)
    assertEquals(3f, area.effect.tickIntervalSeconds)
  }

  @Test
  fun `ore too hard for the caster refuses the cast before anything is spent`() {
    val world = RecordingSkillWorld().apply { blastRefusal = OpError.MINE_ORE_TOO_HARD }

    assertFalse(Blasting().isCastPossible(ctx(world, level = 1)))
    assertEquals(listOf(OpError.MINE_ORE_TOO_HARD), world.toldCaster)
  }

  @Test
  fun `a blast needs a spot on the ground`() {
    val world = RecordingSkillWorld()

    assertTrue(Blasting().isCastPossible(ctx(world, level = 1)))
    assertFalse(Blasting().isCastPossible(SkillContextFixture.skillCtx(BattleContextFixture.entityCtx(), world)))
  }

  private fun ctx(world: RecordingSkillWorld, level: Int) =
    SkillContextFixture.skillCtx(BattleContextFixture.groundCtx(targetPosition = spot), world, skillLevel = level)
}
