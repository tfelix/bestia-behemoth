package net.bestia.worldgen.coast

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.fields.ContourTrace
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.floor

/**
 * The finished surface, sampled finely enough to find a waterline, over the coastal band only.
 *
 * **The band is what makes this affordable.** Evaluating `WorldHeightField` plus a feature evaluator at a
 * hundred-odd metres over a whole world would be millions of samples for a line that touches a thin ribbon of
 * it. Only kilometre cells adjacent to ocean, dilated by one, are covered; everything else is filled with a
 * sentinel that reads as solidly land or solidly sea and so contributes no crossing.
 *
 * [sea] is a separate array from [values] and that separation is load bearing - see
 * [net.bestia.worldgen.fields.ContourTrace]. Which side of the line a cell is on is a *connectivity* question,
 * because an inland basin below sea level is a salt lake and not a coast, and no threshold on elevation can
 * tell the two apart.
 */
class SignField private constructor(
  val width: Int,
  val height: Int,
  val values: DoubleArray,
  val sea: BooleanArray,
  /** Which points carry a real sample rather than the band's sentinel. See [net.bestia.worldgen.fields.ContourTrace]. */
  val valid: BooleanArray,
  private val cellSize: Double,
  private val originX: Double,
  private val originY: Double
) {

  /** A traced contour in grid coordinates, as a polyline in metres. */
  fun toWorld(contour: ContourTrace.Contour): Polyline {
    val points = ArrayList<Vec2d>(contour.size)
    for (i in 0 until contour.size) {
      points.add(Vec2d(originX + contour.xs[i] * cellSize, originY + contour.ys[i] * cellSize))
    }

    // A closed ring's last vertex is not its first, so the polyline has to be told to come back round. An open
    // chain runs off the edge of the grid and must not be.
    if (contour.closed) points.add(points[0])

    return Polyline(points)
  }

  companion object {

    /**
     * Builds the field, or null where the coastal band came out empty.
     *
     * @param ocean the kilometre-resolution ocean mask, as `FlowRouting.oceanMask` produces it
     * @param ground the finished surface: base heightfield plus every feature that touches height
     */
    fun build(
      region: CellRegion,
      ocean: BooleanArray,
      cellSize: Double,
      seaLevel: Double,
      ground: (Double, Double) -> Double
    ): SignField? {
      val metres = region.resolution.metresPerCell
      val band = coastalBand(ocean, region.width, region.height)
      if (band.none { it }) return null

      val world = region.toWorld()
      val width = (world.width / cellSize).toInt() + 1
      val height = (world.height / cellSize).toInt() + 1
      if (width < 2 || height < 2) return null

      val values = DoubleArray(width * height)
      val sea = BooleanArray(width * height)
      val valid = BooleanArray(width * height)

      for (gy in 0 until height) {
        for (gx in 0 until width) {
          val x = world.minX + gx * cellSize
          val y = world.minY + gy * cellSize

          val cx = floor((x - world.minX) / metres).toInt().coerceIn(0, region.width - 1)
          val cy = floor((y - world.minY) / metres).toInt().coerceIn(0, region.height - 1)
          val i = gy * width + gx

          if (!band[cy * region.width + cx]) {
            // Outside the band the answer is already known from the kilometre mask, and saying so with a whole
            // metre either way keeps the interpolation in the tracer well conditioned where the band ends.
            val isOcean = ocean[cy * region.width + cx]
            values[i] = if (isOcean) -1.0 else 1.0
            sea[i] = isOcean
          } else {
            values[i] = ground(x, y) - seaLevel
            valid[i] = true
          }
        }
      }

      floodSea(values, sea, band, ocean, region, world.minX, world.minY, cellSize, metres, width, height)

      return SignField(width, height, values, sea, valid, cellSize, world.minX, world.minY)
    }

    /** Kilometre cells that touch the ocean, dilated by one so the band covers both sides of every waterline. */
    private fun coastalBand(ocean: BooleanArray, width: Int, height: Int): BooleanArray {
      val touching = BooleanArray(ocean.size)

      for (y in 0 until height) {
        for (x in 0 until width) {
          val i = y * width + x
          val here = ocean[i]

          if (x > 0 && ocean[i - 1] != here) touching[i] = true
          if (x < width - 1 && ocean[i + 1] != here) touching[i] = true
          if (y > 0 && ocean[i - width] != here) touching[i] = true
          if (y < height - 1 && ocean[i + width] != here) touching[i] = true
        }
      }

      val band = touching.copyOf()
      for (y in 0 until height) {
        for (x in 0 until width) {
          if (!touching[y * width + x]) continue
          for (dy in -1..1) {
            for (dx in -1..1) {
              val nx = x + dx
              val ny = y + dy
              if (nx in 0 until width && ny in 0 until height) band[ny * width + nx] = true
            }
          }
        }
      }

      return band
    }

    /**
     * Marks the below-sea-level cells that are actually joined to the sea.
     *
     * Seeded from fine cells whose kilometre cell the coarse mask already calls ocean, then flooded through the
     * fine grid. This is the fine-grained twin of `FlowRouting.oceanMask` and it is what keeps a hollow behind
     * a dune - or a lake bed the detail noise happened to push below zero - from acquiring a coastline.
     */
    private fun floodSea(
      values: DoubleArray,
      sea: BooleanArray,
      band: BooleanArray,
      ocean: BooleanArray,
      region: CellRegion,
      originX: Double,
      originY: Double,
      cellSize: Double,
      metres: Double,
      width: Int,
      height: Int
    ) {
      val frontier = IntArray(width * height)
      var top = 0

      for (gy in 0 until height) {
        for (gx in 0 until width) {
          val i = gy * width + gx
          if (sea[i] || values[i] >= 0.0) continue

          val cx = floor(gx * cellSize / metres).toInt().coerceIn(0, region.width - 1)
          val cy = floor(gy * cellSize / metres).toInt().coerceIn(0, region.height - 1)

          // A fine cell below sea level inside a kilometre cell the coarse mask already calls ocean is sea by
          // agreement with the canonical test, and is where the flood starts.
          if (ocean[cy * region.width + cx]) {
            sea[i] = true
            frontier[top++] = i
          }
        }
      }

      while (top > 0) {
        val i = frontier[--top]
        val x = i % width
        val y = i / width

        fun visit(nx: Int, ny: Int) {
          if (nx !in 0 until width || ny !in 0 until height) return
          val j = ny * width + nx
          if (sea[j] || values[j] >= 0.0) return
          sea[j] = true
          frontier[top++] = j
        }

        visit(x - 1, y)
        visit(x + 1, y)
        visit(x, y - 1)
        visit(x, y + 1)
      }
    }
  }
}
