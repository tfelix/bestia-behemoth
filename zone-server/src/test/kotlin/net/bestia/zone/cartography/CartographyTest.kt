package net.bestia.zone.cartography

import io.mockk.every
import io.mockk.mockk
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.cartography.survey.TheodoliteSurveys
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.StructureEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CartographyTest {

  private val theodolite = StructureEntry(
    id = 7, kind = StaticEntityKind.THEODOLITE, ownerMasterId = MASTER_ID, ownerAccountId = 1, position = Vec3L(1, 0, 0),
    yaw = 0f
  )
  private val surveys = mockk<TheodoliteSurveys> {
    every { idleTheodoliteNear(any(), any()) } returns null
    every { idleTheodoliteNear(MASTER_ID, Vec3L(0, 0, 0)) } returns theodolite
  }
  private val sut = Cartography(surveys)
  private val world = testWorld()

  @Test
  fun `a survey at the caster's own idle theodolite may start`() {
    assertNull(sut.checkCastStart(world, surveyorAt(Vec3L(0, 0, 0)), skillLevel = 1, args = listOf(1)))
  }

  @Test
  fun `a survey away from a theodolite is refused`() {
    assertEquals(OpError.SURVEY_NO_THEODOLITE, sut.checkCastStart(world, surveyorAt(Vec3L(50, 0, 0)), 1, listOf(1)))
  }

  @Test
  fun `the bigger surveys open with the skill level`() {
    val surveyor = surveyorAt(Vec3L(0, 0, 0))

    assertEquals(OpError.SURVEY_TIER_LOCKED, sut.checkCastStart(world, surveyor, skillLevel = 2, args = listOf(3)))
    assertNull(sut.checkCastStart(world, surveyor, skillLevel = 3, args = listOf(3)))
    assertEquals(OpError.SURVEY_TIER_LOCKED, sut.checkCastStart(world, surveyor, skillLevel = 4, args = listOf(5)))
  }

  @Test
  fun `a survey size that is no tier is refused as a bad choice`() {
    val surveyor = surveyorAt(Vec3L(0, 0, 0))

    assertEquals(OpError.SKILL_INVALID_CHOICE, sut.checkCastStart(world, surveyor, 5, args = listOf(2)))
    assertEquals(OpError.SKILL_INVALID_CHOICE, sut.checkCastStart(world, surveyor, 5, args = emptyList()))
  }

  private fun surveyorAt(at: Vec3L) = world.createEntity { id ->
    add(id, Master(MASTER_ID))
    add(id, Position.fromVec3(at))
  }

  private companion object {
    const val MASTER_ID = 42L
  }
}
