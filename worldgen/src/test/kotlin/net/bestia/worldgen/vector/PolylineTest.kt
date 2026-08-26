package net.bestia.worldgen.vector

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PolylineTest {

  private val straight = Polyline(listOf(Vec2d(0.0, 0.0), Vec2d(100.0, 0.0)))

  private val elbow = Polyline(
    listOf(Vec2d(0.0, 0.0), Vec2d(100.0, 0.0), Vec2d(100.0, 100.0))
  )

  @Test
  fun `arc length accumulates over segments`() {
    assertEquals(100.0, straight.length, 1e-9)
    assertEquals(200.0, elbow.length, 1e-9)
    assertEquals(100.0, elbow.arcLengthAt(1), 1e-9)
  }

  @Test
  fun `consecutive duplicate points are dropped`() {
    val line = Polyline(
      listOf(Vec2d(0.0, 0.0), Vec2d(0.0, 0.0), Vec2d(10.0, 0.0), Vec2d(10.0, 0.0))
    )

    assertEquals(2, line.vertexCount)
    assertEquals(10.0, line.length, 1e-9)
  }

  @Test
  fun `a line needs two distinct points`() {
    assertFailsWith<IllegalArgumentException> {
      Polyline(listOf(Vec2d(1.0, 1.0), Vec2d(1.0, 1.0)))
    }
  }

  @Test
  fun `projection onto the interior gives arc length and perpendicular distance`() {
    val proj = straight.project(Vec2d(30.0, 7.0))

    assertEquals(30.0, proj.s, 1e-9)
    assertEquals(7.0, proj.distance, 1e-9)
    assertEquals(Vec2d(30.0, 0.0), proj.point)
    assertTrue(!proj.beyondEnd)
  }

  @Test
  fun `lateral offset is positive on the left of the direction of travel`() {
    // Travelling +x, so +y is on the left.
    assertTrue(straight.project(Vec2d(50.0, 5.0)).lateral > 0.0)
    assertTrue(straight.project(Vec2d(50.0, -5.0)).lateral < 0.0)
  }

  @Test
  fun `projection past an end clamps to the endpoint and reports it`() {
    val past = straight.project(Vec2d(140.0, 0.0))

    assertEquals(100.0, past.s, 1e-9)
    assertEquals(40.0, past.distance, 1e-9)
    assertTrue(past.beyondEnd)

    val before = straight.project(Vec2d(-10.0, 0.0))
    assertEquals(0.0, before.s, 1e-9)
    assertTrue(before.beyondEnd)

    // Beside the line near the end is not past the end.
    assertTrue(!straight.project(Vec2d(99.0, 5.0)).beyondEnd)
  }

  @Test
  fun `projection picks the nearest of several segments`() {
    val proj = elbow.project(Vec2d(105.0, 60.0))

    assertEquals(1, proj.segment)
    assertEquals(160.0, proj.s, 1e-9)
    assertEquals(5.0, proj.distance, 1e-9)
  }

  @Test
  fun `station parameter matches the vertex index at vertices`() {
    assertEquals(0.0, elbow.project(Vec2d(0.0, 0.0)).u, 1e-9)
    assertEquals(1.0, elbow.project(Vec2d(100.0, 0.0)).u, 1e-9)
    assertEquals(2.0, elbow.project(Vec2d(100.0, 100.0)).u, 1e-9)
    assertEquals(0.5, elbow.project(Vec2d(50.0, -3.0)).u, 1e-9)
  }

  @Test
  fun `projection is continuous across a vertex`() {
    // The distance-to-polyline field must not jump where two segments meet, or a feature corridor
    // would tear open exactly at the inside of every bend.
    var previous = elbow.project(Vec2d(60.0, 40.0)).distance
    var x = 60.0
    while (x <= 140.0) {
      val current = elbow.project(Vec2d(x, 40.0)).distance
      assertTrue(
        abs(current - previous) < 1.0,
        "distance jumped by ${abs(current - previous)} at x=$x"
      )
      previous = current
      x += 0.5
    }
  }

  @Test
  fun `projection does not depend on how many segments the same shape is split into`() {
    val coarse = Polyline(listOf(Vec2d(0.0, 0.0), Vec2d(300.0, 0.0)))
    val fine = coarse.resample(7.0)

    for (x in 0..300 step 13) {
      val a = coarse.project(Vec2d(x.toDouble(), 12.0))
      val b = fine.project(Vec2d(x.toDouble(), 12.0))
      assertEquals(a.s, b.s, 1e-9)
      assertEquals(a.distance, b.distance, 1e-9)
    }
  }

  @Test
  fun `pointAt is the inverse of projection for points on the line`() {
    for (s in 0..200 step 7) {
      val p = elbow.pointAt(s.toDouble())
      assertEquals(s.toDouble(), elbow.project(p).s, 1e-9)
    }
  }

  @Test
  fun `resample produces uniform spacing and keeps the endpoints`() {
    val resampled = elbow.resample(10.0)

    assertEquals(elbow.points.first(), resampled.points.first())
    assertEquals(elbow.points.last(), resampled.points.last())

    for (i in 1 until resampled.vertexCount) {
      val spacing = resampled.points[i - 1].distanceTo(resampled.points[i])
      assertEquals(10.0, spacing, 1e-6, "segment $i")
    }
  }

  @Test
  fun `truncation cuts inside a segment and keeps the vertices before it`() {
    val cut = elbow.truncatedTo(150.0)

    assertEquals(150.0, cut.length, 1e-9)
    assertEquals(elbow.points.first(), cut.points.first())
    assertEquals(Vec2d(100.0, 50.0), cut.points.last())
    // The corner at 100 m survives; only the tail past the cut is gone.
    assertEquals(3, cut.vertexCount)
  }

  @Test
  fun `truncation at or past the end returns the line itself`() {
    assertEquals(elbow, elbow.truncatedTo(200.0))
    assertEquals(elbow, elbow.truncatedTo(500.0))
  }

  @Test
  fun `truncation to nothing is refused rather than producing a point`() {
    assertFailsWith<IllegalArgumentException> { elbow.truncatedTo(0.0) }
  }

  @Test
  fun `chaikin pins the endpoints and shortens the line`() {
    val staircase = Polyline(
      listOf(
        Vec2d(0.0, 0.0), Vec2d(10.0, 0.0), Vec2d(10.0, 10.0),
        Vec2d(20.0, 10.0), Vec2d(20.0, 20.0)
      )
    )
    val smoothed = staircase.chaikin(3)

    assertEquals(staircase.points.first(), smoothed.points.first())
    assertEquals(staircase.points.last(), smoothed.points.last())
    assertTrue(
      smoothed.length < staircase.length,
      "corner cutting must shorten the line, ${smoothed.length} vs ${staircase.length}"
    )
  }

  @Test
  fun `catmull rom smoothing passes through the original vertices`() {
    val smoothed = elbow.catmullRomSmooth(4)

    for (original in elbow.points) {
      assertTrue(
        smoothed.points.any { it.distanceTo(original) < 1e-9 },
        "$original is not on the smoothed line"
      )
    }
  }

  @Test
  fun `lateral offset displaces perpendicular to the local tangent`() {
    val meandered = straight.resample(10.0).offsetLaterally { s -> 5.0 * kotlin.math.sin(s / 20.0) }

    for (i in meandered.points.indices) {
      val s = i * 10.0
      assertEquals(s, meandered.points[i].x, 1e-9)
      assertEquals(5.0 * kotlin.math.sin(s / 20.0), meandered.points[i].y, 1e-9)
    }
  }
}
