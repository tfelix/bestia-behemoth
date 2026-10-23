package net.bestia.zone.travel.knowledge

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.travel.BridgeCountry
import net.bestia.zone.travel.TravelConfig
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.world.vicinity.Vicinity
import net.bestia.zone.world.vicinity.VicinityIndex
import net.bestia.zone.world.vicinity.VicinitySystem
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RouteSenseTest {

  private val country = BridgeCountry()
  private val knowledge = RouteKnowledge(TravelConfig())
  private val index = VicinityIndex()
  private val sense = RouteSense(country.macroGraph, index, knowledge)

  private val chunkService: ChunkService = mockk {
    every { isReady } returns true
    every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
    every { normalise(any()) } answers { firstArg() }
  }
  private val system = VicinitySystem(chunkService, index, listOf(sense))
  private val world = testWorld()

  private val master = Knower.Player(MASTER_ID)

  private fun bestiaAt(position: Vec3L): EntityId {
    return world.createEntity { id ->
      world.add(id, Position.fromVec3(position))
      world.add(id, Vicinity())
      world.add(id, OwnedBestia(masterId = MASTER_ID, playerBestiaId = id))
    }
  }

  private fun tick() {
    system.update(world, 0.05f)
  }

  @Test
  fun `a bridge that fell far away is not known`() {
    country.breakBridge()
    bestiaAt(BridgeCountry.WEST)

    tick()

    assertFalse(knowledge.believesBlocked(master, country.bridgeEdge))
  }

  @Test
  fun `coming near a fallen bridge teaches the bestia's master`() {
    country.breakBridge()
    bestiaAt(Vec3L(130, 0, 0))

    tick()

    assertTrue(knowledge.believesBlocked(master, country.bridgeEdge))
  }

  @Test
  fun `a bridge falling in sight is learned, and so is its repair`() {
    bestiaAt(Vec3L(130, 0, 0))
    tick()

    country.breakBridge()
    tick()
    assertTrue(knowledge.believesBlocked(master, country.bridgeEdge))

    country.repairBridge()
    tick()
    assertFalse(knowledge.believesBlocked(master, country.bridgeEdge))
  }

  private companion object {
    const val MASTER_ID = 1L
  }
}
