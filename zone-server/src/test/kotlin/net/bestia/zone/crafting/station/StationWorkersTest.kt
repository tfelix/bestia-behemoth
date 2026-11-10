package net.bestia.zone.crafting.station

import net.bestia.zone.crafting.station.StationFixture.Companion.PLAYER_BESTIA
import net.bestia.zone.crafting.station.StationFixture.Companion.STATION
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.world.anchor.AnchorPriority
import net.bestia.zone.world.anchor.SimulationAnchor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StationWorkersTest {

  private val fixture = StationFixture()

  @Test
  fun `a bestia set to work holds its ground like a traveller and goes back to resting after`() {
    val bestia = fixture.givenBestia()

    fixture.workers.assign(fixture.world, bestia, fixture.station)

    assertEquals(STATION, fixture.world.get(bestia, WorkAssignment::class)?.structureId)
    assertEquals(AnchorPriority.WORKER, fixture.world.get(bestia, SimulationAnchor::class)?.priority)

    fixture.workers.unassign(fixture.world, STATION)

    assertNull(fixture.world.get(bestia, WorkAssignment::class))
    assertEquals(AnchorPriority.RESTING, fixture.world.get(bestia, SimulationAnchor::class)?.priority)
    assertNull(fixture.station.workerBestiaId)
  }

  @Test
  fun `a bestia that comes back into the world goes back to its station`() {
    fixture.station = fixture.station.copy(workerBestiaId = PLAYER_BESTIA)
    val bestia = fixture.givenBestia()

    fixture.workers.resume(fixture.world, bestia)

    assertEquals(bestia, fixture.workers.entityOf(STATION))
  }

  @Test
  fun `a new worker takes the station from the old one`() {
    val first = fixture.givenBestia()
    val second = fixture.givenBestia()

    fixture.workers.assign(fixture.world, first, fixture.station)
    fixture.workers.assign(fixture.world, second, fixture.station)

    assertNull(fixture.world.get(first, WorkAssignment::class))
    assertEquals(second, fixture.workers.entityOf(STATION))
  }
}
