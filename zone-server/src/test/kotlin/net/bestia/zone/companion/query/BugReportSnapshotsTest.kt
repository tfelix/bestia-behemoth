package net.bestia.zone.companion.query

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BugReportSnapshotsTest {

  private val world = testWorld()
  private val entities = EntityAOIService()
  private val connections = mockk<ConnectionInfoService>()
  private val worldService = mockk<WorldService> {
    every { config } returns mockk { every { voxelSize } returns 1.0 }
  }
  private val snapshots = BugReportSnapshots(connections, world, entities, worldService)

  @Test
  fun `the master's components and its neighbours are dumped field by field`() {
    val master = placed(Vec3L(10, 20, 0))
    val neighbour = placed(Vec3L(25, 20, 0))
    val farAway = placed(Vec3L(500, 20, 0))
    every { connections.activeMasterIdOf(ACCOUNT) } returns 5
    every { connections.getSelectedMasterEntityId(ACCOUNT) } returns master
    every { connections.getActiveEntityId(ACCOUNT) } returns master

    val reply = snapshots.snapshot("c", ACCOUNT)

    assertEquals(BusReply.Status.OK, reply.status)
    val snapshot = jacksonObjectMapper().readTree((reply.answer as ZoneAnswer.Snapshot).json)
    assertEquals(5, snapshot["masterId"].asInt())
    assertEquals(10, snapshot["master"]["Position"]["_x"].asInt())
    assertFalse(snapshot["master"]["Position"].has("dirtyFlag"))
    assertTrue(snapshot["nearby"].has(neighbour.toString()))
    assertFalse(snapshot["nearby"].has(farAway.toString()))
    assertFalse(snapshot["nearby"].has(master.toString()))
  }

  @Test
  fun `an account without a master in the world has no snapshot`() {
    every { connections.activeMasterIdOf(ACCOUNT) } returns null

    assertEquals(BusReply.Status.NOT_FOUND, snapshots.snapshot("c", ACCOUNT).status)
  }

  private fun placed(at: Vec3L): Long {
    val id = world.createEntity { add(it, Position.fromVec3(at)) }
    entities.setEntityPosition(id, at)
    return id
  }

  private companion object {
    const val ACCOUNT = 42L
  }
}
