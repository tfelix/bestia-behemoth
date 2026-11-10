package net.bestia.zone.cartography.survey

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationSuccessProto.OpSuccess
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.message.OperationSuccessSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import org.junit.jupiter.api.Test

class TheodoliteWorkSystemTest {

  private val structures = mockk<PlayerStructureRegistry>(relaxed = true)
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private val sut = TheodoliteWorkSystem(structures, messages)
  private val world = testWorld(systems = listOf(sut))

  @Test
  fun `the owner at the theodolite works the survey`() {
    surveying(remainingSeconds = 600f)
    standing(Vec3L(1, 0, 0)) { add(it, Master(MASTER_ID)) }

    sut.update(world, 10f)

    verify { structures.setSurvey(STRUCTURE_ID, 1000, 590f) }
  }

  @Test
  fun `an owned bestia at the theodolite works it too`() {
    surveying(remainingSeconds = 600f)
    standing(Vec3L(0, 1, 0)) { add(it, OwnedBestia(MASTER_ID, 3L)) }

    sut.update(world, 10f)

    verify { structures.setSurvey(STRUCTURE_ID, 1000, 590f) }
  }

  @Test
  fun `nobody at the theodolite, or only a stranger, leaves the survey where it is`() {
    surveying(remainingSeconds = 600f)
    standing(Vec3L(30, 0, 0)) { add(it, Master(MASTER_ID)) }
    standing(Vec3L(0, 0, 0)) { add(it, Master(MASTER_ID + 1)) }

    sut.update(world, 10f)

    verify(exactly = 0) { structures.setSurvey(any(), any(), any()) }
  }

  @Test
  fun `the owner is told when the chart waits`() {
    surveying(remainingSeconds = 5f)
    standing(Vec3L(0, 0, 0)) { add(it, Master(MASTER_ID)) }

    sut.update(world, 10f)

    verify { structures.setSurvey(STRUCTURE_ID, 1000, 0f) }
    verify { messages.sendToPlayer(ACCOUNT_ID, OperationSuccessSMSG(OpSuccess.SURVEY_READY)) }
  }

  private fun surveying(remainingSeconds: Float) {
    every { structures.all() } returns listOf(
      StructureEntry(
        id = STRUCTURE_ID, kind = StaticEntityKind.THEODOLITE, ownerMasterId = MASTER_ID, ownerAccountId = ACCOUNT_ID,
        position = Vec3L(0, 0, 0), yaw = 0f, surveyRadiusMetres = 1000,
        surveyRemainingSeconds = remainingSeconds
      )
    )
  }

  private fun standing(at: Vec3L, identity: net.bestia.zone.ecs.core.World.(Long) -> Unit) = world.createEntity { id ->
    add(id, Position.fromVec3(at))
    identity(id)
  }

  private companion object {
    const val MASTER_ID = 42L
    const val ACCOUNT_ID = 1L
    const val STRUCTURE_ID = 7L
  }
}
