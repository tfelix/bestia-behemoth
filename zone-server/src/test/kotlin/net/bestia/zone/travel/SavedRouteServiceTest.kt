package net.bestia.zone.travel

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.PlaceNameService
import net.bestia.zone.place.ecs.PlaceRef
import net.bestia.zone.travel.persistence.SavedRoute
import net.bestia.zone.travel.persistence.SavedRouteRepository
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedRouteServiceTest {

  private val rows = LinkedHashMap<Long, SavedRoute>()
  private var nextId = 1L

  private val repository: SavedRouteRepository = mockk(relaxed = true) {
    val saved = slot<SavedRoute>()
    every { save(capture(saved)) } answers {
      val row = saved.captured
      if (row.id == 0L) SavedRoute::class.java.getDeclaredField("id").apply { isAccessible = true }.set(row, nextId++)
      rows[row.id] = row
      row
    }
    every { findById(any()) } answers { Optional.ofNullable(rows[firstArg()]) }
    every { findAllByMasterIdOrderById(any()) } answers { rows.values.filter { it.masterId == firstArg<Long>() } }
    every { countByMasterId(any()) } answers { rows.values.count { it.masterId == firstArg<Long>() }.toLong() }
    every { delete(any()) } answers { rows.remove(firstArg<SavedRoute>().id) }
  }

  /** West of x = 1000 is Ironhill, east of it Ashford. */
  private val placeNames: PlaceNameService = mockk {
    every { resolve(any(), any()) } answers { PlaceRef(if (firstArg<Long>() < 1000) "Ironhill" else "Ashford") }
  }

  private val service = SavedRouteService(repository, placeNames, TravelConfig(maxSavedRoutes = 2))
  private val mineToTown = listOf(Vec3L(10, 0, 0), Vec3L(500, 300, 0), Vec3L(2000, 0, 0))

  @Test
  fun `an unnamed route is named from where it starts and ends`() {
    val route = service.save(MASTER, mineToTown, name = "  ")

    assertEquals("Ironhill → Ashford", route?.name)
    assertFalse(route!!.customName)
    assertEquals(mineToTown, service.find(MASTER, route.id)?.waypoints)
  }

  @Test
  fun `a renamed route keeps its name, and a blank one gives the made name back`() {
    val route = service.save(MASTER, mineToTown, name = "")!!

    service.rename(MASTER, route.id, "Ore run")
    assertEquals("Ore run", service.find(MASTER, route.id)?.name)

    service.rename(MASTER, route.id, "")
    assertEquals("Ironhill → Ashford", service.find(MASTER, route.id)?.name)
  }

  @Test
  fun `a master keeps no more routes than it may`() {
    service.save(MASTER, mineToTown, "a")
    service.save(MASTER, mineToTown, "b")

    assertNull(service.save(MASTER, mineToTown, "c"))
  }

  @Test
  fun `another master's route can be neither seen, renamed nor deleted`() {
    val route = service.save(MASTER, mineToTown, "Ore run")!!

    assertNull(service.find(OTHER, route.id))
    assertFalse(service.rename(OTHER, route.id, "Mine"))
    assertFalse(service.delete(OTHER, route.id))
    assertTrue(service.delete(MASTER, route.id))
  }

  private companion object {
    const val MASTER = 1L
    const val OTHER = 2L
  }
}
