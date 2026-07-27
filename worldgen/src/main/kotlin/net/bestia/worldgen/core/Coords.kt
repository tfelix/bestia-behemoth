package net.bestia.worldgen.core

import net.bestia.worldgen.vector.Aabb
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Raster resolution, in metres per cell.
 *
 * Resolution is a per-stage property, independent of scale. Climate wants a coarser grid than the
 * heightfield because advection over a fine grid is wasted work; a nested-grid stage wants a finer
 * one. Forcing a single resolution on the whole pipeline is wasteful in both directions.
 */
@JvmInline
value class Resolution(val metresPerCell: Double) {

  init {
    require(metresPerCell > 0.0) { "Resolution must be positive" }
  }

  fun cellsFor(metres: Double) = ceil(metres / metresPerCell).toInt()

  fun toMetres(cells: Int) = cells * metresPerCell

  override fun toString() = "${metresPerCell}m/cell"

  companion object {
    val KILOMETRE = Resolution(1000.0)
    val FOUR_KILOMETRE = Resolution(4000.0)
  }
}

/**
 * A rectangular block of raster cells at a given [resolution], addressed in cell coordinates with
 * the world origin at cell `(0, 0)`.
 *
 * Cell coordinates are only meaningful together with the resolution they were taken at, which is
 * why the two travel together instead of being passed as loose ints.
 */
data class CellRegion(
  val minX: Int,
  val minY: Int,
  val width: Int,
  val height: Int,
  val resolution: Resolution
) {

  init {
    require(width > 0 && height > 0) { "Region must be non-empty, was ${width}x$height" }
  }

  val maxX get() = minX + width - 1
  val maxY get() = minY + height - 1
  val cellCount get() = width.toLong() * height.toLong()

  /** Grows the region by [halo] cells on every side - what a stage's declared halo turns into. */
  fun expanded(halo: Int): CellRegion {
    require(halo >= 0) { "halo must not be negative, was $halo" }
    return copy(
      minX = minX - halo,
      minY = minY - halo,
      width = width + 2 * halo,
      height = height + 2 * halo
    )
  }

  fun contains(x: Int, y: Int) = x in minX..maxX && y in minY..maxY

  /** World-space bounds of this region, in metres. */
  fun toWorld() = Aabb(
    minX * resolution.metresPerCell,
    minY * resolution.metresPerCell,
    (maxX + 1) * resolution.metresPerCell,
    (maxY + 1) * resolution.metresPerCell
  )

  /** The same area expressed at a different resolution, rounded outwards so no area is lost. */
  fun at(target: Resolution): CellRegion {
    val world = toWorld()
    val newMinX = floor(world.minX / target.metresPerCell).toInt()
    val newMinY = floor(world.minY / target.metresPerCell).toInt()
    val newMaxX = ceil(world.maxX / target.metresPerCell).toInt() - 1
    val newMaxY = ceil(world.maxY / target.metresPerCell).toInt() - 1

    return CellRegion(
      newMinX,
      newMinY,
      (newMaxX - newMinX + 1).coerceAtLeast(1),
      (newMaxY - newMinY + 1).coerceAtLeast(1),
      target
    )
  }

  override fun toString() = "CellRegion[($minX,$minY) ${width}x$height @ $resolution]"

  companion object {
    fun world(widthCells: Int, heightCells: Int, resolution: Resolution) =
      CellRegion(0, 0, widthCells, heightCells, resolution)
  }
}

/**
 * A chunk address. The vertical index is part of it because chunks are volumes, but horizontal
 * generation - the heightfield, the vector features - depends on `(x, y)` only.
 */
data class ChunkPos(val x: Int, val y: Int, val z: Int = 0) {

  /** Folded into a single value for RNG derivation and cache keys. */
  fun key(): Long = (x.toLong() and 0x1FFFFF shl 42) or
      (y.toLong() and 0x1FFFFF shl 21) or
      (z.toLong() and 0x1FFFFF)

  override fun toString() = "Chunk($x,$y,$z)"
}
