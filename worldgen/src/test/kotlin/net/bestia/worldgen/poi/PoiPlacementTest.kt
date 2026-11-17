package net.bestia.worldgen.poi

import net.bestia.worldgen.vector.Vec2d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PoiPlacementTest {

  @Test
  fun `copies take the first candidates that keep the spacing`() {
    val waystone = PoiKind.WAYSTONE
    val candidates = listOf(at(0.0), at(1_000.0), at(20_000.0), at(40_000.0))

    val placed = placeAnywhere(request(waystone, listOf(0, 1, 2), candidates))

    assertEquals(listOf(at(0.0), at(20_000.0), at(40_000.0)), placed.map { it.at })
    assertEquals(listOf(0, 1, 2), placed.map { it.copy })
  }

  @Test
  fun `raising the count keeps the copies already placed`() {
    val candidates = listOf(at(0.0), at(20_000.0), at(40_000.0), at(60_000.0))

    val two = placeAnywhere(request(PoiKind.WAYSTONE, listOf(0, 1), candidates))
    val four = placeAnywhere(request(PoiKind.WAYSTONE, listOf(0, 1, 2, 3), candidates))

    assertEquals(two.map { it.at }, four.take(2).map { it.at })
  }

  @Test
  fun `a failed roll leaves a gap in the copy indices`() {
    val candidates = listOf(at(0.0), at(20_000.0))

    val placed = placeAnywhere(request(PoiKind.WAYSTONE, listOf(0, 2), candidates))

    assertEquals(listOf(0, 2), placed.map { it.copy })
  }

  @Test
  fun `an earlier kind is never moved by a later one`() {
    val shared = listOf(at(0.0), at(5_000.0))

    val alone = placeAnywhere(request(PoiKind.LOST_GRAVE, listOf(0), shared))
    // Given in reverse, so the ordinal order is what decides who picks first.
    val together = placeAnywhere(
      request(PoiKind.BROKEN_OBELISK, listOf(0), shared),
      request(PoiKind.LOST_GRAVE, listOf(0), shared)
    )

    assertEquals(alone.single().at, together.first { it.kind == PoiKind.LOST_GRAVE }.at)
    assertEquals(at(5_000.0), together.first { it.kind == PoiKind.BROKEN_OBELISK }.at)
  }

  @Test
  fun `an optional landmark with nowhere to stand is skipped`() {
    val grave = request(PoiKind.LOST_GRAVE, listOf(0), listOf(at(0.0)))

    val placed = PoiPlacement.place(listOf(grave), CLEARANCE, SEED) { false }

    assertTrue(placed.isEmpty())
  }

  @Test
  fun `a required landmark with nowhere to stand fails and names itself`() {
    val error = assertFailsWith<PoiPlacementException> {
      PoiPlacement.place(listOf(request(PoiKind.BROKEN_OBELISK, listOf(0), listOf(at(0.0)))), CLEARANCE, SEED) { false }
    }

    val message = error.message.orEmpty()
    assertTrue("BROKEN_OBELISK" in message && "copy 0" in message && "$SEED" in message, message)
  }

  @Test
  fun `a required landmark without any candidate blames the ground`() {
    val error = assertFailsWith<PoiPlacementException> {
      placeAnywhere(request(PoiKind.BROKEN_OBELISK, listOf(0), emptyList()))
    }

    assertTrue("no dry land" in error.message.orEmpty(), error.message)
  }

  private fun placeAnywhere(vararg requests: PoiPlacement.Request): List<PoiPlacement.Placed> {
    return PoiPlacement.place(requests.toList(), CLEARANCE, SEED) { true }
  }

  private fun request(kind: PoiKind, copies: List<Int>, candidates: List<Vec2d>): PoiPlacement.Request {
    return PoiPlacement.Request(kind, copies, candidates)
  }

  private fun at(x: Double): Vec2d {
    return Vec2d(x, 0.0)
  }

  private companion object {
    const val CLEARANCE = 300.0
    const val SEED = 77L
  }
}
