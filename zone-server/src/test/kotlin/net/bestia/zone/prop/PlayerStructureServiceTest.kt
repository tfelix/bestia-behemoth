package net.bestia.zone.prop

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class PlayerStructureServiceTest {

  private val registry = mockk<PlayerStructureRegistry>(relaxed = true)
  private val placementRule = mockk<PlacementRule>()
  private val messages = mockk<OutMessageProcessor>(relaxed = true)

  private val service = PlayerStructureService(
    structures = registry,
    residency = mockk(relaxed = true),
    source = mockk(relaxed = true),
    sites = mockk(relaxed = true),
    aoi = mockk(relaxed = true),
    worldService = mockk(relaxed = true),
    events = mockk(relaxed = true),
    collision = mockk(relaxed = true),
    placementRules = listOf(placementRule),
    outMessageProcessor = messages,
  )

  @Test
  fun `a structure refused by the placement rules is not started and the builder is told why`() {
    every { placementRule.refusal(StaticEntityKind.FORGE, MASTER_ID, AT) } returns OpError.STRUCTURE_NEEDS_SETTLEMENT

    val site = service.beginConstruction(testWorld(), StaticEntityKind.FORGE, MASTER_ID, ACCOUNT_ID, AT, 0f, 20f)

    assertNull(site)
    verify(exactly = 0) { registry.place(any(), any(), any(), any(), any(), any(), any(), any()) }
    verify { messages.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.STRUCTURE_NEEDS_SETTLEMENT)) }
  }

  private companion object {
    val AT = Vec3L(9000, 9000, 10)
    const val MASTER_ID = 3L
    const val ACCOUNT_ID = 7L
  }
}
