package net.bestia.zone.ecs.spawn.townsfolk

import io.mockk.every
import io.mockk.mockk
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
 * When a door opens, and that opening one costs nothing.
 *
 * The system builds nobody on purpose. A town waking with no player in it should cost a few map removals,
 * and whether anybody is worth materialising is a question only [TownsfolkResidencySystem] can answer -
 * two systems both doing it would put two of the same person in the street.
 */
class IndoorEmergenceSystemTest {

  private val world = testWorld()
  private val registry = IndoorRegistry()

  private var hour = 23
  private val clock = mockk<BestiaClock>().also {
    every { it.now() } answers { BestiaDateTime(year = 1, month = 1, day = 1, hour = hour, minute = 0, second = 0) }
  }

  private val sut = IndoorEmergenceSystem(registry, clock)

  private val sleeper = TownsfolkIdentity.of(settlement = 2, household = 4, member = 0)
  private val nightWatch = TownsfolkIdentity.of(settlement = 2, household = 5, member = 0)

  @Test
  fun `nobody comes out while it is still their night`() {
    registry.enter(sleeper, DOOR, HourWindow(22, 6), 0)

    sut.update(world, DELTA)

    assertTrue(registry.isIndoors(sleeper))
  }

  @Test
  fun `and the record goes when it is over`() {
    registry.enter(sleeper, DOOR, HourWindow(22, 6), 0)
    hour = 6

    sut.update(world, DELTA)

    assertFalse(registry.isIndoors(sleeper))
  }

  @Test
  fun `an empty town wakes up for nothing`() {
    registry.enter(sleeper, DOOR, HourWindow(22, 6), 0)
    hour = 6

    sut.update(world, DELTA)

    assertEquals(0, world.entityCount, "waking a town nobody is in must not build anybody")
  }

  @Test
  fun `each person keeps their own hours`() {
    // The reason the window is stored per record rather than read off a global bedtime: a night watch is
    // indoors during the day, and one rule would turn the whole town out together.
    registry.enter(sleeper, DOOR, HourWindow(22, 6), 0)
    registry.enter(nightWatch, DOOR, HourWindow(6, 14), 0)
    hour = 23

    sut.update(world, DELTA)

    assertFalse(registry.isIndoors(nightWatch), "the watch is due on duty")
    assertTrue(registry.isIndoors(sleeper), "and everybody else is asleep")
  }

  private companion object {
    val DOOR = Vec3L(50, 60, 2)
    const val DELTA = 20f
  }
}
