package net.bestia.zone.world.vicinity

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VicinitySystemTest {

  private val chunkService: ChunkService = mockk {
    every { isReady } returns true
    every { config } returns WorldConfig(seed = 1L, chunkSize = 32, voxelSize = 1.0)
    every { normalise(any()) } answers { firstArg() }
  }

  /** What each entity was told it came near, in order. */
  private val told = mutableListOf<Pair<EntityId, Set<ChunkColumn>>>()

  private val sense = object : VicinitySense {
    override fun onEntered(world: World, entity: EntityId, columns: Collection<ChunkColumn>) {
      told.add(entity to columns.toSet())
    }
  }

  private val index = VicinityIndex()
  private val system = VicinitySystem(chunkService, index, listOf(sense))
  private val world = testWorld()

  private fun sensorAt(position: Vec3L): EntityId {
    return world.createEntity { id ->
      world.add(id, Position.fromVec3(position))
      world.add(id, Vicinity())
    }
  }

  private fun tick() {
    system.update(world, 0.05f)
  }

  @Test
  fun `a new sensor is told about the three by three around it`() {
    val sensor = sensorAt(Vec3L(0, 0, 0))

    tick()

    assertEquals(1, told.size)
    assertEquals(9, told.single().second.size)
    assertEquals(setOf(sensor), index.sensorsIn(ChunkColumn(1, 1)))
  }

  @Test
  fun `standing still or walking inside a chunk tells it nothing`() {
    val sensor = sensorAt(Vec3L(0, 0, 0))
    tick()

    moveTo(sensor, Vec3L(31, 31, 0))
    tick()
    tick()

    assertEquals(1, told.size)
  }

  @Test
  fun `crossing a chunk tells it only the new row`() {
    val sensor = sensorAt(Vec3L(0, 0, 0))
    tick()

    moveTo(sensor, Vec3L(32, 0, 0))
    tick()

    assertEquals(setOf(ChunkColumn(2, -1), ChunkColumn(2, 0), ChunkColumn(2, 1)), told.last().second)
    assertTrue(index.sensorsIn(ChunkColumn(-1, 0)).isEmpty(), "the row behind is left")
  }

  @Test
  fun `a sensor that goes away leaves no trace`() {
    val sensor = sensorAt(Vec3L(0, 0, 0))
    tick()

    world.destroy(sensor)
    tick()

    assertEquals(0, index.size)
    assertTrue(index.sensorsIn(ChunkColumn(0, 0)).isEmpty())
  }

  private fun moveTo(id: EntityId, to: Vec3L) {
    world.getOrThrow(id, Position::class).apply {
      x = to.x
      y = to.y
      z = to.z
    }
  }
}
