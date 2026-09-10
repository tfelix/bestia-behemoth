package net.bestia.zone.ecs.spawn.townsfolk

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ai.core.state.HourWindow
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The other half of going indoors: something outside has to decide when the door opens.
 *
 * There is nothing left of a person who went inside - the entity was destroyed - so this is the only
 * thing that can bring them back, and the failure mode if it is wrong is a town that empties one night
 * and never fills again.
 */
class IndoorEmergenceSystemTest {

  private val world = testWorld()
  private val registry = IndoorRegistry()
  private val spawner = mockk<TownsfolkEntitySpawner>()

  private var hour = 23

  private val clock = mockk<BestiaClock>().also {
    every { it.now() } answers { BestiaDateTime(year = 1, month = 1, day = 1, hour = hour, minute = 0, second = 0) }
  }

  private val sut = IndoorEmergenceSystem(registry, spawner, clock)

  private val sleeper = TownsfolkIdentity.of(settlement = 2, household = 4, member = 0)
  private val nightWatch = TownsfolkIdentity.of(settlement = 2, household = 5, member = 0)

  @Test
  fun `nobody comes out while it is still their night`() {
    registry.enter(sleeper, DOOR, HourWindow(22, 6))
    every { spawner.emerge(any(), any(), any()) } returns 1L

    sut.update(world, DELTA)

    verify(exactly = 0) { spawner.emerge(any(), any(), any()) }
    assertTrue(registry.isIndoors(sleeper))
  }

  @Test
  fun `and does when it is over, at the door they went in by`() {
    registry.enter(sleeper, DOOR, HourWindow(22, 6))
    every { spawner.emerge(any(), any(), any()) } returns 1L
    hour = 6

    sut.update(world, DELTA)

    verify(exactly = 1) { spawner.emerge(world, sleeper, DOOR) }
    assertFalse(registry.isIndoors(sleeper), "the record has to go, or they come out again every sweep")
  }

  @Test
  fun `each person keeps their own hours`() {
    // The reason the window is stored per record rather than read off a global bedtime: a night watch is
    // indoors during the day, and a sweep that used one rule would turn the whole town out together.
    registry.enter(sleeper, DOOR, HourWindow(22, 6))
    registry.enter(nightWatch, DOOR, HourWindow(6, 14))
    every { spawner.emerge(any(), any(), any()) } returns 1L
    hour = 23

    sut.update(world, DELTA)

    verify(exactly = 1) { spawner.emerge(world, nightWatch, DOOR) }
    assertTrue(registry.isIndoors(sleeper))
  }

  @Test
  fun `a person the settlement can no longer place is dropped rather than retried`() {
    // The world regenerated, or the town shrank and the household went with it. Leaving the record would
    // have the sweep ask for the same impossible person for the rest of the process's life.
    registry.enter(sleeper, DOOR, HourWindow(22, 6))
    every { spawner.emerge(any(), any(), any()) } returns null
    hour = 6

    sut.update(world, DELTA)

    assertFalse(registry.isIndoors(sleeper))
    assertEquals(0, registry.size)
  }

  private companion object {
    val DOOR = Vec3L(50, 60, 2)
    const val DELTA = 20f
  }
}
