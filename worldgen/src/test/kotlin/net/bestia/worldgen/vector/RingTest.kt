package net.bestia.worldgen.vector

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a [Ring] promises that a closed [Polyline] does not.
 *
 * Every test here is a claim the areal features rest on, and each was confirmed to go red against a
 * deliberately broken version of the thing it tests - the winding normalisation removed, the half-open
 * edge rule made inclusive, the fixed-point conversion done on the difference instead of on the absolute
 * coordinate, the wrap-pair exemption dropped from the self-intersection check.
 */
class RingTest {

  /** A unit square, counter-clockwise, sitting well away from the origin so quantisation has work to do. */
  private fun square(size: Double = 100.0, at: Vec2d = Vec2d(41_000.0, 27_500.0)) = Ring(
    listOf(
      Vec2d(at.x, at.y),
      Vec2d(at.x + size, at.y),
      Vec2d(at.x + size, at.y + size),
      Vec2d(at.x, at.y + size)
    )
  )

  @Test
  fun `winding is normalised so a clockwise caller gets the same ring`() {
    val ccw = square()
    val cw = Ring(square().vertices.reversed())

    assertEquals(ccw.vertices.toSet(), cw.vertices.toSet(), "same vertices")
    // The interesting part is not the vertex set but that the *orientation* agrees, because every sign
    // convention downstream reads it. Compare as a cyclic sequence rather than a list: normalising is
    // allowed to rotate the starting vertex, and does when it reverses.
    val ccwFrom = ccw.vertices.indexOf(cw.vertices.first())
    assertTrue(ccwFrom >= 0, "the reversed ring's first vertex is somewhere in the original")
    for (i in cw.vertices.indices) {
      assertEquals(
        ccw.vertices[(ccwFrom + i) % ccw.vertexCount],
        cw.vertices[i],
        "vertex $i of the clockwise ring, walked cyclically"
      )
    }
    assertEquals(ccw.area, cw.area, 1e-9, "area")
  }

  @Test
  fun `a query on the same row as a vertex is counted exactly once`() {
    // A diamond, so two of the four vertices sit exactly on the horizontal through the centre. A naive
    // crossing count double-counts a ray that passes through a vertex and reports the interior as
    // outside; the half-open rule is what fixes it.
    val c = Vec2d(8_000.0, 8_000.0)
    val diamond = Ring(
      listOf(
        Vec2d(c.x, c.y - 50.0),
        Vec2d(c.x + 50.0, c.y),
        Vec2d(c.x, c.y + 50.0),
        Vec2d(c.x - 50.0, c.y)
      )
    )

    assertTrue(diamond.contains(c.x, c.y), "the centre, on the row of two vertices, is inside")
    assertFalse(diamond.contains(c.x - 60.0, c.y), "left of the west vertex is outside")
    assertFalse(diamond.contains(c.x + 60.0, c.y), "right of the east vertex is outside")

    // And the same for the topmost and bottommost vertices, where only one edge pair meets the row.
    assertFalse(diamond.contains(c.x, c.y - 60.0), "below the south vertex is outside")
    assertFalse(diamond.contains(c.x, c.y + 60.0), "above the north vertex is outside")
  }

  @Test
  fun `containment is a function of the quantised position, so two chunks agree`() {
    // Aimed at the boundary, because everywhere else is not a decision. A position in open ground answers
    // the same with or without quantisation; the only place the two implementations can differ is where
    // the cross product is exactly zero, which is *on* an edge. So every probe here sits on one.
    val ring = Ring(
      listOf(
        Vec2d(41_000.0, 27_500.0),
        Vec2d(41_061.0, 27_500.0),
        Vec2d(41_061.0, 27_580.0),
        Vec2d(41_020.0, 27_640.0),
        Vec2d(41_000.0, 27_580.0)
      )
    )

    var checked = 0
    for (i in 0 until ring.vertexCount) {
      val a = ring.vertex(i)
      val b = ring.vertex(i + 1)
      for (step in 0..20) {
        val t = step / 20.0
        val onEdge = a.lerp(b, t)
        // The same world position spelled two ways that quantise identically - which is what two chunks
        // reaching a shared column centre by different arithmetic actually produce.
        for (nudge in listOf(1e-9, -1e-9, 1e-7, -1e-7)) {
          val nx = onEdge.x + nudge
          val ny = onEdge.y + nudge
          if (Quantize.toFixed(nx) != Quantize.toFixed(onEdge.x)) continue
          if (Quantize.toFixed(ny) != Quantize.toFixed(onEdge.y)) continue
          assertEquals(
            ring.contains(onEdge.x, onEdge.y),
            ring.contains(nx, ny),
            "edge $i at t=$t, nudged by $nudge"
          )
          checked++
        }
      }
    }
    assertTrue(checked > 200, "only $checked knife-edge positions were actually compared")
  }

  @Test
  fun `the interior that containment reports has the area the shoelace says`() {
    // The test that actually pins the crossing rule. `contains` and `area` are computed by completely
    // different arithmetic - a parity count in fixed point against a shoelace sum in doubles - so agreeing
    // on the enclosed area to within a lattice step is a strong statement that the parity count is right.
    //
    // Both weaker tests above pass under a mixed rule (`yi > qy` against `yj >= qy`) that double-counts
    // every vertex row, because a symmetric diamond is symmetric under the mistake. This does not: a
    // double-counted row is a row of interior reported as exterior, and it shows up as missing area.
    //
    // Every vertex sits on a whole metre and the lattice steps in whole metres, so *every* vertex row is
    // sampled and the vertex-on-ray case is exercised on every one of them rather than by luck.
    val ring = Ring(
      listOf(
        Vec2d(1_000.0, 2_000.0),
        Vec2d(1_040.0, 2_000.0),
        Vec2d(1_040.0, 2_030.0),
        Vec2d(1_020.0, 2_030.0),
        Vec2d(1_020.0, 2_050.0),
        Vec2d(1_060.0, 2_050.0),
        Vec2d(1_060.0, 2_080.0),
        Vec2d(1_000.0, 2_080.0)
      )
    )

    val step = 0.25
    var inside = 0
    var y = ring.bbox.minY - 5.0
    while (y <= ring.bbox.maxY + 5.0) {
      // Rows land on whole metres so every vertex row is sampled. Columns are offset by half a step so
      // they never land on a vertical edge, where a sample is neither in nor out by half a cell and would
      // add a percent of quantisation noise to the comparison for no extra coverage.
      var x = ring.bbox.minX - 5.0 + step * 0.5
      while (x <= ring.bbox.maxX + 5.0) {
        if (ring.contains(x, y)) inside++
        x += step
      }
      y += step
    }

    val counted = inside * step * step
    // By rows: 40 wide for 30, then 20 wide for 20, then 60 wide for 30 = 1200 + 400 + 1800.
    assertEquals(3_400.0, ring.area, 1e-6, "shoelace")
    assertTrue(
      abs(counted - ring.area) < ring.area * 0.02,
      "containment enclosed $counted m2 against a shoelace area of ${ring.area} m2"
    )
  }

  @Test
  fun `a crescent is not star-shaped about its centroid`() {
    // The whole justification for a vertex ring over a radial r(theta): this shape cannot be written as a
    // single-valued function of the angle from any interior point, and the oxbow lake producer needs it.
    val crescent = Ring.crescent(
      centre = Vec2d(20_000.0, 15_000.0),
      radius = 300.0,
      bearing = Vec2d(1.0, 0.0),
      bite = 0.8,
      seed = 12L
    )

    // Some ray from the centroid must leave and re-enter, which is exactly what a single-valued radius
    // cannot express. Count sign changes of `contains` along each ray.
    var maxCrossings = 0
    for (step in 0 until 180) {
      val theta = step * PI / 90.0
      var crossings = 0
      var previous = crescent.contains(crescent.centroid)
      var r = 1.0
      while (r < 700.0) {
        val here = crescent.contains(
          crescent.centroid.x + cos(theta) * r,
          crescent.centroid.y + sin(theta) * r
        )
        if (here != previous) crossings++
        previous = here
        r += 0.5
      }
      if (crossings > maxCrossings) maxCrossings = crossings
    }

    assertTrue(maxCrossings >= 2, "no ray crossed the boundary more than once; this is not a crescent")
  }

  @Test
  fun `a figure eight is refused`() {
    // Lopsided on purpose. A symmetric bowtie has a signed area of exactly zero and so is caught by the
    // degeneracy check instead, which would have made this test pass for the wrong reason - it did, on the
    // first attempt, and the message assertion below is what exposed it.
    val bowtie = listOf(
      Vec2d(0.0, 0.0),
      Vec2d(100.0, 120.0),
      Vec2d(100.0, 0.0),
      Vec2d(0.0, 100.0)
    )
    val failure = assertFailsWith<IllegalArgumentException> { Ring(bowtie) }
    assertTrue(failure.message!!.contains("simple"), "message was: ${failure.message}")
  }

  @Test
  fun `a legitimately concave ring is accepted`() {
    // The self-intersection check must not be a convexity check. An L, which every settlement outline in
    // a river bend will be some version of.
    val ell = Ring(
      listOf(
        Vec2d(0.0, 0.0),
        Vec2d(300.0, 0.0),
        Vec2d(300.0, 100.0),
        Vec2d(100.0, 100.0),
        Vec2d(100.0, 300.0),
        Vec2d(0.0, 300.0)
      )
    )

    assertTrue(ell.contains(50.0, 250.0), "the tall arm")
    assertTrue(ell.contains(250.0, 50.0), "the wide arm")
    assertFalse(ell.contains(250.0, 250.0), "the notch is outside")
  }

  @Test
  fun `arc length and the station parameter wrap instead of clamping`() {
    val ring = square()

    assertEquals(0.0, ring.arcLengthAt(0), 1e-12)
    assertEquals(0.0, ring.arcLengthAt(ring.vertexCount), 1e-12, "vertex n is vertex 0")
    assertEquals(ring.vertex(1), ring.vertex(ring.vertexCount + 1), "index wraps in both directions")
    assertEquals(ring.vertex(ring.vertexCount - 1), ring.vertex(-1), "and negatively")

    // Every projection lands strictly inside [0, vertexCount) - never at vertexCount, which is where a
    // clamped implementation would pile up everything past the last vertex.
    for (step in 0 until 400) {
      val theta = step * 2.0 * PI / 400
      val p = Vec2d(
        ring.centroid.x + cos(theta) * 400.0,
        ring.centroid.y + sin(theta) * 400.0
      )
      val u = ring.project(p).u
      assertTrue(u >= 0.0 && u < ring.vertexCount, "u was $u at theta $theta")
    }
  }

  @Test
  fun `the seam is not a special place on the boundary`() {
    // The failure a closed Polyline would have: vertex 0 is an endpoint there, so a position just before
    // it and one just after it project to arc lengths a whole perimeter apart. Here they must be close
    // together in *space*, which is what a profile reads.
    val ring = Ring.warpedCircle(Vec2d(5_000.0, 5_000.0), radius = 200.0, seed = 7L)
    val v0 = ring.vertex(0)
    val before = ring.vertex(-1)
    val after = ring.vertex(1)

    val justBefore = before.lerp(v0, 0.999)
    val justAfter = v0.lerp(after, 0.001)

    val a = ring.project(justBefore)
    val b = ring.project(justAfter)

    assertTrue(a.point.distanceTo(b.point) < 1.0, "projections landed ${a.point.distanceTo(b.point)} m apart")
    assertTrue(a.distance < 1e-6 && b.distance < 1e-6, "both points are on the boundary")

    // The arc lengths *are* a perimeter apart - that is correct and is what wrapping means. What must not
    // happen is the distance or the projected point jumping.
    val wrapped = kotlin.math.min(abs(a.s - b.s), ring.perimeter - abs(a.s - b.s))
    assertTrue(wrapped < 1.0, "wrapped arc-length gap was $wrapped m")
  }

  @Test
  fun `signed distance changes sign exactly where the magnitude vanishes`() {
    val ring = Ring.warpedCircle(Vec2d(-3_000.0, 12_000.0), radius = 150.0, seed = 3L)

    for (step in 0 until 120) {
      val theta = step * 2.0 * PI / 120
      var previous = ring.signedDistance(ring.centroid.x, ring.centroid.y)
      var r = 1.0
      while (r < 260.0) {
        val x = ring.centroid.x + cos(theta) * r
        val y = ring.centroid.y + sin(theta) * r
        val d = ring.signedDistance(x, y)
        if ((d < 0.0) != (previous < 0.0)) {
          // At a sign change both samples must be within a step of the boundary; the flip cannot happen
          // out in open ground, which is what an inconsistent sign source would look like.
          assertTrue(abs(d) < 1.0, "sign flipped ${abs(d)} m from the boundary at theta $theta")
        }
        previous = d
        r += 0.5
      }
    }
  }

  @Test
  fun `a repeated closing vertex is dropped rather than making a zero-length segment`() {
    val open = square()
    val closed = Ring(open.vertices + open.vertices.first())

    assertEquals(open.vertexCount, closed.vertexCount)
    assertEquals(open.perimeter, closed.perimeter, 1e-9)
  }

  @Test
  fun `degenerate rings are refused`() {
    assertFailsWith<IllegalArgumentException>("two points") {
      Ring(listOf(Vec2d(0.0, 0.0), Vec2d(1.0, 0.0)))
    }
    assertFailsWith<IllegalArgumentException>("three collinear points") {
      Ring(listOf(Vec2d(0.0, 0.0), Vec2d(1.0, 0.0), Vec2d(2.0, 0.0)))
    }
    assertFailsWith<IllegalArgumentException>("too many vertices") {
      Ring((0..Ring.MAX_VERTICES).map {
        val t = it * 2.0 * PI / (Ring.MAX_VERTICES + 1)
        Vec2d(cos(t) * 100.0, sin(t) * 100.0)
      })
    }
  }

  @Test
  fun `the centroid does not depend on which way the caller wound the ring`() {
    // Every other fixture in this file is counter-clockwise, and that is how a reflected centroid survived
    // the whole suite: the sign only matters when `init` has to reverse the vertices. What caught it was the
    // first producer whose shapes came out clockwise, reporting its centroid a hundred and fifty kilometres
    // outside the world. A centroid is also not cosmetic - `contains` rejects against a disc centred on it,
    // so a wrong one makes containment answer false everywhere.
    val ccw = square(size = 200.0, at = Vec2d(3_000.0, 4_000.0))
    val cw = Ring(ccw.vertices.reversed())

    assertEquals(ccw.centroid.x, cw.centroid.x, 1e-9)
    assertEquals(ccw.centroid.y, cw.centroid.y, 1e-9)
    assertEquals(3_100.0, cw.centroid.x, 1e-9, "and it is where the square actually is")
    assertEquals(4_100.0, cw.centroid.y, 1e-9)
    assertTrue(cw.contains(3_100.0, 4_100.0), "containment still works on a clockwise caller's ring")
  }

  @Test
  fun `area and centroid are the geometric ones, not the vertex average`() {
    val ring = square(size = 200.0, at = Vec2d(0.0, 0.0))
    assertEquals(40_000.0, ring.area, 1e-6)
    assertEquals(100.0, ring.centroid.x, 1e-9)
    assertEquals(100.0, ring.centroid.y, 1e-9)

    // A square with one edge finely subdivided: the vertex mean drifts towards the subdivided edge, the
    // area centroid does not. This is why `warpedCircle`, whose vertices are unevenly spaced by
    // construction, needs the area form.
    val lopsided = Ring(
      listOf(Vec2d(0.0, 0.0), Vec2d(200.0, 0.0), Vec2d(200.0, 200.0)) +
          (0..8).map { Vec2d(200.0 - it * 25.0, 200.0) } +
          listOf(Vec2d(0.0, 200.0))
    )
    assertEquals(100.0, lopsided.centroid.x, 1e-6, "x is unmoved by the extra vertices")
    assertEquals(100.0, lopsided.centroid.y, 1e-6, "y is unmoved by the extra vertices")
  }

  @Test
  fun `a fan lobe has its apex as a genuine corner`() {
    val apex = Vec2d(1_000.0, 2_000.0)
    val fan = Ring.fanLobe(apex, Vec2d(0.0, 1.0), length = 400.0, spread = 0.9, seed = 5L)

    assertTrue(fan.vertices.any { it.distanceTo(apex) < 1e-9 }, "the apex is a vertex")
    // The lobe opens downstream: its centroid is well ahead of the apex.
    assertTrue(fan.centroid.y > apex.y + 100.0, "centroid at ${fan.centroid}")
    assertFalse(fan.contains(apex.x, apex.y - 10.0), "nothing upstream of the apex is in the fan")
  }
}
