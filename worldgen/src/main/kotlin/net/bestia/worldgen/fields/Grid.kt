package net.bestia.worldgen.fields

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Parallel

/**
 * A mutable scalar grid in local `0 until width` coordinates, used *inside* a stage while it works.
 *
 * Deliberately separate from [FloatLayer]. A layer is immutable, addressed in world cell coordinates
 * and carries an identity; a grid is scratch space for an algorithm that will read and write the
 * same cells a hundred times. Priority-flood, D8 routing, stream power and thermal relaxation all
 * want the second thing, and doing them through the layer API would mean a bounds-clamping branch
 * per neighbour access in the innermost loop of the most expensive stage in the pipeline.
 *
 * Values are doubles here even though layers store floats: erosion accumulates over dozens of
 * timesteps, and float rounding in the accumulator shows up as visible terracing on gentle slopes.
 */
class Grid(val width: Int, val height: Int, val data: DoubleArray) {

  init {
    require(width > 0 && height > 0) { "Grid must be non-empty, was ${width}x$height" }
    require(data.size == width * height) {
      "Grid ${width}x$height needs ${width * height} values, got ${data.size}"
    }
  }

  constructor(width: Int, height: Int, value: Double = 0.0) :
      this(width, height, DoubleArray(width * height) { value })

  constructor(width: Int, height: Int, init: (x: Int, y: Int) -> Double) :
      this(width, height, DoubleArray(width * height) { init(it % width, it / width) })

  val size get() = width * height

  fun index(x: Int, y: Int) = y * width + x

  fun inBounds(x: Int, y: Int) = x >= 0 && y >= 0 && x < width && y < height

  /** Clamped at the edges, which is the no-flux boundary every kernel in this package wants. */
  operator fun get(x: Int, y: Int): Double =
    data[index(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))]

  operator fun set(x: Int, y: Int, value: Double) {
    data[index(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))] = value
  }

  fun copy() = Grid(width, height, data.copyOf())

  fun fill(value: Double) = data.fill(value)

  fun min() = data.min()
  fun max() = data.max()

  fun mean(): Double {
    var sum = 0.0
    for (v in data) sum += v
    return sum / data.size
  }

  /** Largest absolute gradient to any neighbour - what "slope" means for biome and soil scoring. */
  fun gradient(x: Int, y: Int, metresPerCell: Double): Double {
    // Central differences over the four cardinal neighbours: less directionally biased than a
    // forward difference, and it does not spike by a factor of sqrt(2) on diagonal-facing slopes.
    val dzdx = (this[x + 1, y] - this[x - 1, y]) / (2.0 * metresPerCell)
    val dzdy = (this[x, y + 1] - this[x, y - 1]) / (2.0 * metresPerCell)
    return kotlin.math.sqrt(dzdx * dzdx + dzdy * dzdy)
  }

  /** In-place box blur, [iterations] passes. Cheap eddy mixing for the climate fields. */
  fun blur(iterations: Int, radius: Int = 1) {
    require(iterations >= 0 && radius >= 1) { "iterations >= 0 and radius >= 1 required" }

    var source = data
    var target = DoubleArray(data.size)
    repeat(iterations) {
      // Safe to split because the pass already reads `source` and writes `target` and never the same
      // array - the double buffer that was there to make the result order-independent is exactly the
      // property that makes the rows separable. Each band owns its output rows and reads whatever it
      // likes, since nothing in this pass writes what another band reads.
      val from = source
      val into = target
      Parallel.rows(height, width) { yFrom, yUntil ->
        for (y in yFrom until yUntil) {
          for (x in 0 until width) {
            var sum = 0.0
            var count = 0
            for (dy in -radius..radius) {
              for (dx in -radius..radius) {
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                sum += from[ny * width + nx]
                count++
              }
            }
            into[y * width + x] = sum / count
          }
        }
      }
      val swap = source
      source = target
      target = swap
    }

    if (source !== data) source.copyInto(data)
  }

  fun toLayer(id: LayerId, region: CellRegion): FloatLayer {
    require(region.width == width && region.height == height) {
      "Grid ${width}x$height does not match $region"
    }
    return FloatLayer(id, region, FloatArray(data.size) { data[it].toFloat() })
  }

  override fun toString() = "Grid[${width}x$height]"

  companion object {

    /**
     * The same grid as `Grid(width, height, init)`, with the rows split across cores.
     *
     * A separate factory rather than a change to the constructor, deliberately. The constructor has around
     * fifteen call sites and not all of their `init` lambdas are pure - some close over a scratch buffer or
     * a cursor, and splitting those would be a race that produces a plausible-looking world rather than a
     * crash. So conversion is opt-in and per site, and the reviewer of each one has to have looked.
     *
     * [init] must be a pure function of `(x, y)` and of state nothing else in the loop writes.
     */
    inline fun parallel(width: Int, height: Int, crossinline init: (x: Int, y: Int) -> Double): Grid {
      val data = DoubleArray(width * height)
      Parallel.rows(height, width) { yFrom, yUntil ->
        for (y in yFrom until yUntil) {
          val row = y * width
          for (x in 0 until width) data[row + x] = init(x, y)
        }
      }
      return Grid(width, height, data)
    }

    /** The grid of a layer, in local coordinates. */
    fun from(layer: FloatLayer): Grid {
      val region = layer.region
      return Grid(region.width, region.height, DoubleArray(layer.data.size) { layer.data[it].toDouble() })
    }

    /**
     * A grid over [region] filled by sampling another layer at each cell centre in world space.
     *
     * This is how a stage at one resolution reads an upstream layer at another - climate runs on 4 km
     * cells and lifts a 1 km heightfield, biomes run on 1 km cells and lift a 4 km climate field.
     * Going through world coordinates rather than index arithmetic means neither stage has to know
     * the other's resolution, which is the whole point of resolution being per-stage.
     */
    fun resampled(source: FloatLayer, region: CellRegion): Grid {
      val metres = region.resolution.metresPerCell
      // A sixteen-tap bicubic per destination cell, and this runs a dozen times across the pipeline as
      // stages lift each other's fields between resolutions. Reading an immutable layer, so it splits.
      return parallel(region.width, region.height) { x, y ->
        source.sampleBicubic((region.minX + x + 0.5) * metres, (region.minY + y + 0.5) * metres)
      }
    }
  }
}

/** The integer counterpart of [Grid]: plate ids, flow directions, basin labels, biome ids. */
class IntGrid(val width: Int, val height: Int, val data: IntArray) {

  init {
    require(width > 0 && height > 0) { "IntGrid must be non-empty, was ${width}x$height" }
    require(data.size == width * height) {
      "IntGrid ${width}x$height needs ${width * height} values, got ${data.size}"
    }
  }

  constructor(width: Int, height: Int, value: Int = 0) :
      this(width, height, IntArray(width * height) { value })

  val size get() = width * height

  fun index(x: Int, y: Int) = y * width + x

  fun inBounds(x: Int, y: Int) = x >= 0 && y >= 0 && x < width && y < height

  operator fun get(x: Int, y: Int): Int =
    data[index(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))]

  operator fun set(x: Int, y: Int, value: Int) {
    data[index(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))] = value
  }

  fun count(value: Int) = data.count { it == value }

  fun toLayer(id: LayerId, region: CellRegion): IntLayer {
    require(region.width == width && region.height == height) {
      "IntGrid ${width}x$height does not match $region"
    }
    return IntLayer(id, region, data.copyOf())
  }

  override fun toString() = "IntGrid[${width}x$height]"
}

/**
 * The eight-neighbour offsets, in one fixed order.
 *
 * The order is part of the contract, not an implementation detail: D8 flow direction ties are broken
 * by taking the first steepest neighbour, so two nodes must scan the neighbours in the same sequence
 * or they will route a flat cell differently and the two river networks will diverge.
 */
object D8 {

  /** East, north-east, north, north-west, west, south-west, south, south-east. */
  val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
  val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

  /**
   * The same eight directions in words, for anything that shows a flow direction to a person.
   *
   * Indexed identically to [DX] and [DY], and that is the point of keeping it here rather than in the viewer:
   * the order above is contractual, and a separate copy of it somewhere else is a copy that can disagree.
   */
  val NAMES = arrayOf("E", "NE", "N", "NW", "W", "SW", "S", "SE")

  /** Centre-to-centre distance in cells, so diagonals are not treated as one cell of travel. */
  val LENGTH = DoubleArray(8) { if (DX[it] != 0 && DY[it] != 0) SQRT_2 else 1.0 }

  const val NONE = -1

  private const val SQRT_2 = 1.4142135623730951
}
