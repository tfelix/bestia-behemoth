package net.bestia.zone.ecs.place

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The index's whole claim is that a lookup needs one bucket, not a neighbourhood.
 *
 * That rests on an area being written into every cell its radius covers, which is easy to get subtly wrong
 * and impossible to notice: the failure is a town whose name appears in the middle and vanishes near the
 * edge, which reads as a boundary rather than as a bug.
 */
class AreaNameRegistryTest {

  private val lattice = AreaNameRegistry.LATTICE_METRES

  @Test
  fun `an area is found from every point inside it, not only near its centre`() {
    val registry = AreaNameRegistry()
    // Deliberately larger than one lattice cell and off-centre in it, so a naive centre-cell-only index
    // would pass at the middle and fail at the rim.
    val radius = lattice * 2 + 300
    val centre = lattice * 5 + 137

    registry.add(entry(id = 1, x = centre, y = centre, radius = radius))

    for (offset in listOf(0L, radius / 2, radius - 1)) {
      for (direction in listOf(1L, -1L)) {
        val x = centre + offset * direction
        assertNotNull(registry.at(x, centre), "missed the area at x offset ${offset * direction}")
        assertNotNull(registry.at(centre, x), "missed the area at y offset ${offset * direction}")
      }
    }
  }

  @Test
  fun `a point outside the radius is not in the area even inside its cell`() {
    val registry = AreaNameRegistry()
    // A small area well inside one cell: its own cell contains ground it does not.
    registry.add(entry(id = 1, x = lattice * 3 + 500, y = lattice * 3 + 500, radius = 100))

    assertNotNull(registry.at(lattice * 3 + 500, lattice * 3 + 500))
    assertNull(
      registry.at(lattice * 3 + 700, lattice * 3 + 500),
      "the bucket read must still test the radius, or a cell would name everything in it"
    )
  }

  @Test
  fun `removing an area clears every cell it was written into`() {
    val registry = AreaNameRegistry()
    val radius = lattice * 3
    registry.add(entry(id = 1, x = lattice * 6, y = lattice * 6, radius = radius))

    registry.remove(1)

    // The rim, not the centre: a removal that only cleared the centre cell would pass a centre check.
    assertNull(registry.at(lattice * 6 + radius - 1, lattice * 6))
    assertNull(registry.at(lattice * 6, lattice * 6))
    assertEquals(0, registry.size)
  }

  @Test
  fun `adding the same id twice replaces it rather than duplicating it`() {
    val registry = AreaNameRegistry()
    registry.add(entry(id = 1, x = 0, y = 0, radius = 500, name = "Ashford"))
    registry.add(entry(id = 1, x = 0, y = 0, radius = 500, name = "Ashfell"))

    assertEquals(1, registry.size)
    assertEquals("Ashfell", registry.at(0, 0)?.name)
  }

  @Test
  fun `the narrowest area containing a point wins`() {
    val registry = AreaNameRegistry()
    // A claim inside a town, both containing the origin. The rule is the server's whole answer to
    // overlapping areas, so it is asserted here rather than left to PlaceNameService.
    registry.add(entry(id = -1, x = 0, y = 0, radius = 900, name = "Ashford"))
    registry.add(entry(id = 1, x = 0, y = 0, radius = 200, name = "Newtown"))

    assertEquals("Newtown", registry.at(0, 0)?.name)
    assertEquals("Ashford", registry.at(500, 0)?.name, "outside the claim the town is still the answer")
  }

  @Test
  fun `an exact tie in radius is broken by id, not by insertion order`() {
    val forwards = AreaNameRegistry()
    forwards.add(entry(id = 1, x = 0, y = 0, radius = 500, name = "First"))
    forwards.add(entry(id = 2, x = 0, y = 0, radius = 500, name = "Second"))

    val backwards = AreaNameRegistry()
    backwards.add(entry(id = 2, x = 0, y = 0, radius = 500, name = "Second"))
    backwards.add(entry(id = 1, x = 0, y = 0, radius = 500, name = "First"))

    // Two boots of one world must name a place the same way, and bucket order is not something to trust.
    assertEquals("First", forwards.at(0, 0)?.name)
    assertEquals("First", backwards.at(0, 0)?.name)
  }

  @Test
  fun `generated settlements and player claims cannot collide on an id`() {
    val registry = AreaNameRegistry()

    // The namespacing rule: a settlement is -(index + 1) and a claim is its own positive row id. Index 0
    // is the case that would collide under a naive scheme, since a row id of 0 is also plausible.
    registry.add(entry(id = -1, x = 0, y = 0, radius = 500, name = "Ashford"))
    registry.add(entry(id = 0, x = 0, y = 0, radius = 400, name = "Newtown"))

    assertEquals(2, registry.size, "the claim replaced the settlement instead of joining it")
    assertEquals("Newtown", registry.at(0, 0)?.name, "both are there, and the tighter one wins")
  }

  @Test
  fun `clearing drops everything, for a world that was replaced under us`() {
    val registry = AreaNameRegistry()
    registry.add(entry(id = 1, x = 0, y = 0, radius = 500))

    registry.clear()

    assertEquals(0, registry.size)
    assertNull(registry.at(0, 0))
  }

  private fun entry(
    id: Long,
    x: Long,
    y: Long,
    radius: Long,
    name: String = "Ashford"
  ): AreaNameRegistry.Entry {
    return AreaNameRegistry.Entry(id = id, name = name, x = x, y = y, radius = radius)
  }
}
