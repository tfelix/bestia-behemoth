package net.bestia.worldgen.voxel

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The removal brush: the shape, the fractions it produces, and the floor it refuses to go below.
 *
 * The volume assertions are the load-bearing ones. A brush that reports the wrong fraction per voxel writes
 * occupancy that is wrong by the same amount, and occupancy is what both the client's surface and this side's
 * walkability are reconstructed from - so a brush that is quietly 10% generous carves tunnels the server thinks
 * are wider than the player sees.
 */
class CarveBrushTest {

  private fun volumeOf(brush: CarveBrush): Double {
    var total = 0.0
    brush.forEachVoxel { _, _, _, removed -> total += removed }
    return total
  }

  @Test
  fun `a sphere removes its own volume`() {
    for (radius in listOf(2.0, 2.5, 3.0, 5.0)) {
      val expected = 4.0 / 3.0 * Math.PI * radius * radius * radius
      val actual = volumeOf(CarveBrush.sphere(100.5, 200.5, 40.5, radius))

      // Supersampling, so the error lives in the boundary shell. The worst case measured against the analytic
      // volume over these radii is 0.6%; one percent leaves margin for the lattice-alignment oscillation
      // described on CarveBrush.SAMPLES while still catching a factor or a systematic bias.
      assertTrue(
        abs(actual - expected) / expected < 0.01,
        "a sphere of radius $radius removed $actual, expected about $expected"
      )
    }
  }

  @Test
  fun `a capsule removes a cylinder plus its two caps`() {
    val radius = 2.0
    val length = 10.0
    val brush = CarveBrush.capsule(100.5, 200.5, 40.5, 100.5 + length, 200.5, 40.5, radius)

    val expected = Math.PI * radius * radius * length + 4.0 / 3.0 * Math.PI * radius * radius * radius
    val actual = volumeOf(brush)

    assertTrue(
      abs(actual - expected) / expected < 0.01,
      "a capsule of radius $radius and length $length removed $actual, expected about $expected"
    )
  }

  /**
   * A sphere and a zero-length capsule are the same brush, not merely similar.
   *
   * The degenerate case is the whole reason there is one shape here instead of two, so if it ever stops being
   * exact then the segment clamp has grown a branch it should not have.
   */
  @Test
  fun `a zero length capsule is a sphere`() {
    val sphere = CarveBrush.sphere(10.5, 20.5, 30.5, 2.5)
    val degenerate = CarveBrush.capsule(10.5, 20.5, 30.5, 10.5, 20.5, 30.5, 2.5)

    assertEquals(volumeOf(sphere), volumeOf(degenerate))
  }

  @Test
  fun `voxels well inside are taken whole and voxels well outside are not touched`() {
    val brush = CarveBrush.sphere(50.5, 50.5, 50.5, 4.0)

    assertEquals(1.0, brush.removedFractionOf(50, 50, 50), "the voxel at the centre should go entirely")
    assertEquals(0.0, brush.removedFractionOf(50, 50, 60), "a voxel ten out should not be touched")
    assertEquals(0.0, brush.removedFractionOf(0, 0, 0), "a voxel far away should not be touched")
  }

  /**
   * The bounding box is tight enough to be worth having and loose enough to lose nothing.
   *
   * Every voxel the brush takes any part of has to be inside it, because [CarveBrush.forEachVoxel] iterates the
   * box and nothing else - a box one voxel short would silently leave a rind of half-carved rock behind.
   */
  @Test
  fun `the bounds contain every voxel the brush touches`() {
    val brush = CarveBrush.sphere(16.25, 32.75, -8.5, 3.0)

    for (voxelZ in brush.minVoxelZ - 2..brush.maxVoxelZ + 2) {
      for (voxelY in brush.minVoxelY - 2..brush.maxVoxelY + 2) {
        for (voxelX in brush.minVoxelX - 2..brush.maxVoxelX + 2) {
          val inBounds = voxelX in brush.minVoxelX..brush.maxVoxelX &&
              voxelY in brush.minVoxelY..brush.maxVoxelY &&
              voxelZ in brush.minVoxelZ..brush.maxVoxelZ

          if (!inBounds) {
            assertEquals(
              0.0,
              brush.removedFractionOf(voxelX, voxelY, voxelZ),
              "($voxelX,$voxelY,$voxelZ) is outside the bounds but is touched"
            )
          }
        }
      }
    }
  }

  /** Sea level is voxel zero, so half the world is at a negative index and a brush has to work there. */
  @Test
  fun `a brush below sea level behaves the same as one above it`() {
    val above = volumeOf(CarveBrush.sphere(0.5, 0.5, 120.5, 2.5))
    val below = volumeOf(CarveBrush.sphere(0.5, 0.5, -120.5, 2.5))

    assertEquals(above, below)
  }

  /**
   * The floor is enforced at construction, because that is the only place it cannot be forgotten.
   *
   * Below about 1.3 voxels the client's mesher draws nothing at all, so a smaller brush removes rock the player
   * can still see - and the server then answers line of sight and movement through it. See the class note on
   * [CarveBrush] for the measurements.
   */
  @Test
  fun `a brush below the resolution floor is refused`() {
    for (radius in listOf(0.5, 1.0, 1.2, CarveBrush.MIN_RADIUS - 0.01)) {
      assertFailsWith<IllegalArgumentException>("radius $radius should have been refused") {
        CarveBrush.sphere(0.5, 0.5, 0.5, radius)
      }
    }

    CarveBrush.sphere(0.5, 0.5, 0.5, CarveBrush.MIN_RADIUS)
  }

  /**
   * The minimum brush is big enough for the client to draw, by the rule that measured it.
   *
   * Apparent bore runs about `2r - 1`, so the floor has to leave a gallery a player can walk down rather than a
   * pinhole. This is the assertion that fails if [CarveBrush.MIN_RADIUS] is ever lowered towards the cliff;
   * `CarveVisibilityTest` on the client is the other half, measured against the real mesher.
   */
  @Test
  fun `the minimum radius bores a gallery rather than a pinhole`() {
    assertTrue(
      2.0 * CarveBrush.MIN_RADIUS - 1.0 >= 3.0,
      "MIN_RADIUS ${CarveBrush.MIN_RADIUS} bores only ${2.0 * CarveBrush.MIN_RADIUS - 1.0} voxels across"
    )
  }

  /**
   * Fractions are a smooth function of distance, which is what makes a tunnel wall smooth.
   *
   * A brush that returned only 0 and 1 would satisfy every volume assertion above to within its own error and
   * still produce stair-stepped walls - the thing occupancy exists to avoid.
   */
  @Test
  fun `voxels on the boundary are partially removed`() {
    val brush = CarveBrush.sphere(0.5, 0.5, 0.5, 2.0)

    val partial = buildList {
      brush.forEachVoxel { _, _, _, removed -> if (removed > 0.0 && removed < 1.0) add(removed) }
    }

    assertTrue(partial.size > 20, "only ${partial.size} voxels were partially removed")
  }

  /**
   * The brush is a function of its shape alone, so two brushes over the same rock agree about it.
   *
   * The same property `ChunkSeamCheck` demands of the generator, and for the same reason: a carve that spans two
   * chunks is evaluated once per chunk, and the two evaluations have to produce identical bytes or the tunnel
   * has a seam down the middle of it.
   */
  @Test
  fun `two brushes with the same shape agree voxel for voxel`() {
    val a = CarveBrush.sphere(31.5, 31.5, 63.5, 2.5)
    val b = CarveBrush.sphere(31.5, 31.5, 63.5, 2.5)

    a.forEachVoxel { x, y, z, removed ->
      assertEquals(removed, b.removedFractionOf(x, y, z), "($x,$y,$z) disagreed between two identical brushes")
    }
  }

  /** A capsule at an angle is still a capsule: nothing is taken beyond its radius from the axis. */
  @Test
  fun `nothing outside the radius is removed`() {
    val radius = 2.5
    val brush = CarveBrush.capsule(0.5, 0.5, 0.5, 6.5, 3.5, 2.5, radius)

    brush.forEachVoxel { x, y, z, _ ->
      // The nearest point of the voxel to the axis must be within the radius, so measuring from the voxel
      // centre allows the half-diagonal a corner can add.
      val distance = distanceToAxis(x + 0.5, y + 0.5, z + 0.5, brush)

      assertTrue(
        distance <= radius + sqrt(3.0) / 2.0 + 1e-9,
        "($x,$y,$z) is $distance from the axis but the radius is $radius"
      )
    }
  }

  private fun distanceToAxis(px: Double, py: Double, pz: Double, brush: CarveBrush): Double {
    val sx = brush.toX - brush.fromX
    val sy = brush.toY - brush.fromY
    val sz = brush.toZ - brush.fromZ
    val lengthSquared = sx * sx + sy * sy + sz * sz

    val ox = px - brush.fromX
    val oy = py - brush.fromY
    val oz = pz - brush.fromZ

    val t = if (lengthSquared <= 0.0) 0.0 else ((ox * sx + oy * sy + oz * sz) / lengthSquared).coerceIn(0.0, 1.0)

    val dx = ox - t * sx
    val dy = oy - t * sy
    val dz = oz - t * sz

    return sqrt(dx * dx + dy * dy + dz * dz)
  }
}
