package net.bestia.worldgen.vector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FeatureSystemTest {

  private fun line(x0: Double, y0: Double, x1: Double, y1: Double) =
    Polyline(listOf(Vec2d(x0, y0), Vec2d(x1, y1)))

  private fun road(id: Long, x0: Double, y0: Double, x1: Double, y1: Double, surface: Double = 90.0) =
    LinearFeatures.road(
      id = FeatureId(id),
      centerline = line(x0, y0, x1, y1),
      surfaceElevation = { surface },
      halfWidth = { 5.0 },
      shoulder = { 10.0 }
    )

  private fun moraine(id: Long, x0: Double, y0: Double, x1: Double, y1: Double) =
    LinearFeatures.moraine(
      id = FeatureId(id),
      centerline = line(x0, y0, x1, y1),
      halfWidth = { 40.0 },
      ridgeHeight = { 25.0 }
    )

  @Test
  fun `features are stamped in priority order regardless of input order`() {
    val ridge = moraine(1, 0.0, 0.0, 200.0, 0.0)
    val track = road(2, 0.0, 0.0, 200.0, 0.0)

    // Moraine (350, additive) then road (500, replace): the road cuts through the ridge, so the
    // surface is the road's. If the order flipped, the ridge would be piled on top of the road.
    val ordered = FeatureEvaluator(listOf(ridge, track)).heightAt(100.0, 0.0, 100.0)
    val shuffled = FeatureEvaluator(listOf(track, ridge)).heightAt(100.0, 0.0, 100.0)

    assertEquals(90.0, ordered, 1e-9)
    assertEquals(ordered, shuffled, 0.0)
  }

  @Test
  fun `a later feature sees the height an earlier one produced`() {
    // The moraine is additive, so it piles onto whatever is there. Adding it over open ground and
    // over a carved channel must differ by exactly the carve.
    val ridge = moraine(1, 0.0, 0.0, 200.0, 0.0)
    val onGround = FeatureEvaluator(listOf(ridge)).heightAt(100.0, 0.0, 100.0)
    val onCarved = FeatureEvaluator(listOf(ridge)).heightAt(100.0, 0.0, 80.0)

    assertEquals(20.0, onGround - onCarved, 1e-9)
  }

  @Test
  fun `a feature outside its corridor leaves the terrain alone`() {
    val track = road(1, 0.0, 0.0, 200.0, 0.0)

    assertEquals(137.0, FeatureEvaluator(listOf(track)).heightAt(100.0, 400.0, 137.0), 1e-9)
  }

  @Test
  fun `an empty evaluator is a no-op`() {
    val evaluator = FeatureEvaluator(emptyList())

    assertTrue(evaluator.isEmpty)
    assertEquals(42.0, evaluator.heightAt(1.0, 2.0, 42.0), 0.0)
  }

  @Test
  fun `the index returns every feature whose corridor reaches the query area`() {
    val near = road(1, 0.0, 0.0, 100.0, 0.0)
    val far = road(2, 10_000.0, 10_000.0, 10_100.0, 10_000.0)
    val index = FeatureIndex.build(listOf(near, far))

    val hits = index.query(Aabb(40.0, -1.0, 60.0, 1.0))

    assertEquals(listOf(FeatureId(1)), hits.map { it.id })
  }

  @Test
  fun `the index finds a feature whose geometry misses the area but whose corridor does not`() {
    // The corridor is 15 m wide; a query 10 m to the side must still hit, or the far bank of a
    // river would be missing from the chunk next door.
    val track = road(1, 0.0, 0.0, 100.0, 0.0)
    val index = FeatureIndex.build(listOf(track))

    assertEquals(1, index.query(Aabb(50.0, 10.0, 51.0, 11.0)).size)
    assertEquals(0, index.query(Aabb(50.0, 500.0, 51.0, 501.0)).size)
  }

  @Test
  fun `index results are ordered by priority then id`() {
    val features = listOf(
      road(9, 0.0, 0.0, 100.0, 0.0),
      moraine(7, 0.0, 0.0, 100.0, 0.0),
      road(3, 0.0, 0.0, 100.0, 0.0),
      moraine(5, 0.0, 0.0, 100.0, 0.0)
    )

    val hits = FeatureIndex.build(features).query(Aabb(40.0, -1.0, 60.0, 1.0))

    // Moraines (350) before roads (500); within a kind, ascending id.
    assertEquals(
      listOf(FeatureId(5), FeatureId(7), FeatureId(3), FeatureId(9)),
      hits.map { it.id }
    )
  }

  @Test
  fun `index results do not depend on the order features were handed in`() {
    val features = (1L..40L).map { road(it, it * 10.0, 0.0, it * 10.0 + 60.0, 0.0) }
    val area = Aabb(0.0, -50.0, 500.0, 50.0)

    val forward = FeatureIndex.build(features).query(area).map { it.id }
    val backward = FeatureIndex.build(features.reversed()).query(area).map { it.id }

    assertEquals(forward, backward)
    assertEquals(forward, forward.sortedBy { it.value })
  }

  @Test
  fun `duplicate feature ids are rejected`() {
    assertFailsWith<IllegalArgumentException> {
      FeatureIndex.build(listOf(road(1, 0.0, 0.0, 10.0, 0.0), road(1, 50.0, 0.0, 60.0, 0.0)))
    }
  }

  @Test
  fun `an empty index answers queries without blowing up`() {
    assertEquals(emptyList(), FeatureIndex.empty().query(Aabb(-100.0, -100.0, 100.0, 100.0)))
  }

  @Test
  fun `station count must match the centerline`() {
    val centerline = line(0.0, 0.0, 100.0, 0.0)
    val stations = StationTable.Builder(3)
      .channel(PolylineFeature.CORRIDOR_CHANNEL, doubleArrayOf(10.0, 10.0, 10.0))
      .build()

    assertFailsWith<IllegalArgumentException> {
      PolylineFeature(
        id = FeatureId(1),
        kind = FeatureKind.ROAD,
        centerline = centerline,
        stations = stations,
        profile = { _, _, _, base -> base }
      )
    }
  }
}
