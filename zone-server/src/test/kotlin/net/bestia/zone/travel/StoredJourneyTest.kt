package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoredJourneyTest {

  @Test
  fun `only the stops still ahead are kept, and come back as a player's journey`() {
    val journey = Journey(listOf(Vec3L(1, 0, 0), Vec3L(9, 9, 0)), JourneyOrder.PLAYER, name = "Mine → Ashford")
    journey.advanceToNextWaypoint()

    val restored = StoredJourney.decode(StoredJourney.of(journey)?.encode())?.toJourney()

    assertEquals(listOf(Vec3L(9, 9, 0)), restored?.waypoints)
    assertEquals("Mine → Ashford", restored?.name)
    assertEquals(JourneyOrder.PLAYER, restored?.order)
  }

  @Test
  fun `a creature's own journey is not kept`() {
    assertNull(StoredJourney.of(Journey(listOf(Vec3L(1, 0, 0)), JourneyOrder.AI)))
  }

  @Test
  fun `a row that cannot be read any more is no journey`() {
    assertNull(StoredJourney.decode("not json"))
    assertNull(StoredJourney.decode(null))
  }
}
