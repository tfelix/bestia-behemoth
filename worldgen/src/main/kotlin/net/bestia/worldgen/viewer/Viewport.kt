package net.bestia.worldgen.viewer

import net.bestia.worldgen.vector.Aabb
import kotlin.math.max
import kotlin.math.min

/**
 * The world-space window a frame is rendered through.
 *
 * World y grows north; screen y grows down. The flip lives here and nowhere else, so no renderer
 * or overlay has to remember it - a map that comes out upside down is the kind of bug that wastes
 * an afternoon precisely because it looks plausible.
 */
data class Viewport(
  val centerX: Double,
  val centerY: Double,
  val metresPerPixel: Double,
  val widthPx: Int,
  val heightPx: Int
) {

  init {
    require(metresPerPixel > 0.0) { "metresPerPixel must be positive, was $metresPerPixel" }
    require(widthPx > 0 && heightPx > 0) { "Viewport must be non-empty, was ${widthPx}x$heightPx" }
  }

  val minX get() = centerX - widthPx * metresPerPixel / 2.0
  val minY get() = centerY - heightPx * metresPerPixel / 2.0

  val bounds: Aabb
    get() = Aabb(minX, minY, minX + widthPx * metresPerPixel, minY + heightPx * metresPerPixel)

  /** World x of the centre of pixel column [px]. */
  fun worldX(px: Int) = minX + (px + 0.5) * metresPerPixel

  /** World y of the centre of pixel row [py], counting rows from the top. */
  fun worldY(py: Int) = minY + (heightPx - py - 0.5) * metresPerPixel

  fun screenX(worldX: Double) = (worldX - minX) / metresPerPixel - 0.5

  fun screenY(worldY: Double) = heightPx - 0.5 - (worldY - minY) / metresPerPixel

  fun resized(widthPx: Int, heightPx: Int) = copy(widthPx = widthPx, heightPx = heightPx)

  /** Drags the map with the mouse: the world moves with the pointer, not against it. */
  fun pannedByPixels(dxPixels: Int, dyPixels: Int) = copy(
    centerX = centerX - dxPixels * metresPerPixel,
    centerY = centerY + dyPixels * metresPerPixel
  )

  /**
   * Zooms about a pixel, keeping the world position under it fixed - the behaviour a mouse wheel
   * needs if you are trying to follow a river upstream and not lose it on every notch.
   */
  fun zoomedAt(px: Int, py: Int, factor: Double): Viewport {
    require(factor > 0.0) { "zoom factor must be positive, was $factor" }

    val anchorX = worldX(px)
    val anchorY = worldY(py)
    val scale = metresPerPixel / factor

    // The anchor must stay at the same pixel, so solve for the centre that puts it there.
    return copy(
      centerX = anchorX - (px + 0.5 - widthPx / 2.0) * scale,
      centerY = anchorY - (heightPx - py - 0.5 - heightPx / 2.0) * scale,
      metresPerPixel = scale
    )
  }

  /**
   * Zooms about the centre exactly.
   *
   * Not `zoomedAt(widthPx / 2, ...)`: with an even width the centre falls *between* two pixels, so
   * anchoring on either of them drifts the view by half a pixel per notch.
   */
  fun zoomedAtCenter(factor: Double): Viewport {
    require(factor > 0.0) { "zoom factor must be positive, was $factor" }
    return copy(metresPerPixel = metresPerPixel / factor)
  }

  override fun toString() =
    "Viewport[centre=(${"%.1f".format(centerX)}, ${"%.1f".format(centerY)}) " +
        "${"%.3f".format(metresPerPixel)} m/px ${widthPx}x$heightPx]"

  companion object {

    /** The view that shows all of [area], with a little margin, centred. */
    fun fit(area: Aabb, widthPx: Int, heightPx: Int, margin: Double = 1.05): Viewport {
      val perPixel = max(
        max(area.width / widthPx, area.height / heightPx) * margin,
        MIN_METRES_PER_PIXEL
      )

      return Viewport(
        centerX = (area.minX + area.maxX) / 2.0,
        centerY = (area.minY + area.maxY) / 2.0,
        metresPerPixel = perPixel,
        widthPx = widthPx,
        heightPx = heightPx
      )
    }

    /** Below a millimetre per pixel there is nothing left to look at, and the maths gets silly. */
    const val MIN_METRES_PER_PIXEL = 1e-3

    fun clampScale(metresPerPixel: Double, world: Aabb) =
      min(max(metresPerPixel, MIN_METRES_PER_PIXEL), max(world.width, world.height))
  }
}
