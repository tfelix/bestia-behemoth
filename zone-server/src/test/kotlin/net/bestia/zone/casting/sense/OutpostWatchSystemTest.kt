package net.bestia.zone.casting.sense

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.bestia.diary.BestiaHappened
import net.bestia.zone.casting.sense.BrewingEvents.Brewing
import net.bestia.zone.casting.sense.BrewingEvents.Stage
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** An outpost at the origin and a rift knot 5 km to the east, inside the watch of an outpost for a big event. */
class OutpostWatchSystemTest {

  private val world = testWorld()
  private val outpost = StructureEntry(1, StaticEntityKind.OBSERVATION_OUTPOST, 1, 7, Vec3L(0, 0, 0), 0f)
  private val structures = mockk<PlayerStructureRegistry> {
    every { `in`(any(), any()) } returns emptyList()
    every { `in`(0, 0) } returns listOf(outpost)
  }
  private val worldService = mockk<WorldService> { every { config } returns WorldConfig(seed = 1, widthCells = 128, heightCells = 128) }
  private val told = mutableListOf<BestiaHappened>()
  private var events = listOf(Brewing(5_000.0, 0.0, "mana", "rift", feltFrom = 3.0, stage = Stage.STIRRING))

  private val sut = OutpostWatchSystem(structures, worldService, { told += it }, { events })

  @Test
  fun `a bestia at the outpost tells its master once of an event stirring in range`() {
    bestiaAt(Vec3L(3, 2, 0))

    sut.update(world, 60f)
    sut.update(world, 60f)

    assertEquals(1, told.size)
    assertEquals(BestiaHappened.Kind.EVENT_SENSED, told.single().kind)
    assertEquals(9L, told.single().masterId)
    assertEquals("mana", told.single().reason)
  }

  @Test
  fun `a bestia away from the outpost keeps no watch`() {
    bestiaAt(Vec3L(200, 0, 0))

    sut.update(world, 60f)

    assertEquals(0, told.size)
  }

  @Test
  fun `a small event beyond the watch is not felt`() {
    events = listOf(Brewing(5_000.0, 0.0, "beasts", "apex-roamer", feltFrom = 1.0, stage = Stage.STIRRING))
    bestiaAt(Vec3L(3, 2, 0))

    sut.update(world, 60f)

    assertEquals(0, told.size)
  }

  private fun bestiaAt(at: Vec3L) = world.createEntity { id ->
    add(id, OwnedBestia(masterId = 9, playerBestiaId = 42))
    add(id, Position.fromVec3(at))
  }
}
