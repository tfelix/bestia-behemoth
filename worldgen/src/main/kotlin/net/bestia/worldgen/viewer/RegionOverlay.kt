package net.bestia.worldgen.viewer

import net.bestia.worldgen.place.PlaceRegions
import net.bestia.worldgen.render.Viewport
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Line2D

/**
 * Draws the place-name partition: a tint per region, its borders, and its name at its centre.
 *
 * ### The one place these borders are allowed to be drawn
 *
 * `place/PlaceRegions` rasterises its partition on the one-kilometre base grid, against the advice
 * `climate/WeatherRegions` gives for its own, and the argument turns entirely on the borders never being
 * drawn *to a player*: a one-kilometre staircase is invisible when the only thing observable is the metre
 * at which a label changes. That argument is what makes drawing them **here** necessary rather than
 * contradictory. The cost weights decide whether a border lands on a ridge or half way between two seeds,
 * and there is no number that shows it - `RegionCalibrationTest` can report that regions average seven
 * kilometres across and still not notice that every border is a straight line.
 *
 * So: if a border ever wants to be in front of a player it needs contouring and smoothing first. Until
 * then this is the only renderer that has one.
 *
 * ### Built on demand
 *
 * `NavGraphOverlay`'s argument, and the same shape: a run that never turns this on should not pay for a
 * Dijkstra over the whole world. `WorldScene` holds it behind a `by lazy`.
 */
class RegionOverlay(private val regions: PlaceRegions) {

  /**
   * Draws the cells of the partition, not the pixels of the screen.
   *
   * One cell is one `fillRect`, so a zoomed-out view costs a few thousand of them instead of one per
   * pixel, and a zoomed-in view shows the kilometre staircase at its real size rather than a smoothed
   * version of it. Showing the staircase is the point: it is the cost this design accepted, and a
   * renderer that hid it would be hiding the thing worth judging.
   *
   * Each cell is still resolved through [PlaceRegions.indexAt] - the same call the game makes for a
   * player's position - rather than by reading the assignment array directly, so a fold-or-clamp mistake
   * in that lookup shows up here instead of only in front of a player.
   */
  fun draw(g: Graphics2D, view: Viewport, labels: Boolean = true): Stats {
    val cell = regions.cellSize
    val bounds = view.bounds

    val firstX = Math.floor(bounds.minX / cell).toInt()
    val lastX = Math.floor(bounds.maxX / cell).toInt()
    val firstY = Math.floor(bounds.minY / cell).toInt()
    val lastY = Math.floor(bounds.maxY / cell).toInt()

    val across = lastX - firstX + 1
    val down = lastY - firstY + 1
    if (across <= 0 || down <= 0) return Stats.NONE

    val indices = IntArray(across * down)
    for (cy in 0 until down) {
      for (cx in 0 until across) {
        indices[cy * across + cx] = regions.indexAt(
          (firstX + cx + 0.5) * cell,
          (firstY + cy + 0.5) * cell
        )
      }
    }

    tint(g, view, indices, firstX, firstY, across, down, cell)
    val bordered = borders(g, view, indices, firstX, firstY, across, down, cell)
    val drawn = if (labels) labels(g, view, indices) else 0

    return Stats(regionsOnScreen = indices.toHashSet().size, borderEdges = bordered, labels = drawn)
  }

  /**
   * A flat wash per region, keyed on the index.
   *
   * Deliberately not keyed on [net.bestia.worldgen.place.RegionKind]: two neighbours of the same kind are
   * exactly the pair whose border needs to be visible, and colouring by kind would hide it. The kind is
   * legible from the name instead.
   */
  private fun tint(
    g: Graphics2D,
    view: Viewport,
    indices: IntArray,
    firstX: Int,
    firstY: Int,
    across: Int,
    down: Int,
    cell: Double
  ) {
    for (cy in 0 until down) {
      for (cx in 0 until across) {
        val region = indices[cy * across + cx]
        g.color = colorOf(region, regions.regions.getOrNull(region)?.isWater ?: false)
        fillCell(g, view, firstX + cx, firstY + cy, cell)
      }
    }
  }

  private fun borders(
    g: Graphics2D,
    view: Viewport,
    indices: IntArray,
    firstX: Int,
    firstY: Int,
    across: Int,
    down: Int,
    cell: Double
  ): Int {
    g.color = BORDER
    g.stroke = BasicStroke(BORDER_WIDTH)
    var painted = 0

    for (cy in 0 until down) {
      for (cx in 0 until across) {
        val here = indices[cy * across + cx]
        val right = if (cx + 1 < across) indices[cy * across + cx + 1] else here
        val above = if (cy + 1 < down) indices[(cy + 1) * across + cx] else here

        val worldX = firstX + cx
        val worldY = firstY + cy

        // Only the edge that actually divides two regions, not the whole cell. Filling the cell reads as
        // a border at a kilometre per pixel and as a black square at a metre per pixel, where one cell
        // covers the screen.
        if (here != right) {
          drawEdge(g, view, (worldX + 1) * cell, worldY * cell, (worldX + 1) * cell, (worldY + 1) * cell)
          painted++
        }
        if (here != above) {
          drawEdge(g, view, worldX * cell, (worldY + 1) * cell, (worldX + 1) * cell, (worldY + 1) * cell)
          painted++
        }
      }
    }

    return painted
  }

  private fun drawEdge(
    g: Graphics2D,
    view: Viewport,
    fromX: Double,
    fromY: Double,
    toX: Double,
    toY: Double
  ) {
    g.draw(
      Line2D.Double(
        view.screenX(fromX),
        view.screenY(fromY),
        view.screenX(toX),
        view.screenY(toY)
      )
    )
  }

  /**
   * Fills one partition cell, tiling exactly with its neighbours.
   *
   * The extent is the difference between two *rounded edges*, not the rounding of a width. Rounding the
   * width independently per cell leaves a one-pixel gap wherever two roundings disagree, and at eleven
   * pixels per cell that happened often enough to draw a faint regular lattice across the whole world -
   * which read as a grid the partition did not have.
   */
  private fun fillCell(g: Graphics2D, view: Viewport, cellX: Int, cellY: Int, cell: Double) {
    val left = Math.round(view.screenX(cellX * cell)).toInt()
    val right = Math.round(view.screenX((cellX + 1) * cell)).toInt()

    // Screen y runs the other way from world y, so the cell's top edge is its higher world coordinate.
    val top = Math.round(view.screenY((cellY + 1) * cell)).toInt()
    val bottom = Math.round(view.screenY(cellY * cell)).toInt()

    g.fillRect(left, top, Math.max(1, right - left), Math.max(1, bottom - top))
  }

  /**
   * One label per region visible on screen, at the region's centroid.
   *
   * Skips a region whose centroid is off screen even though part of it is visible: a label pinned to the
   * edge of the view would move as the view panned, which reads as a different place rather than the same
   * one seen from further along. A region too small to hold its own text is skipped for the same reason.
   */
  private fun labels(g: Graphics2D, view: Viewport, indices: IntArray): Int {
    g.font = Font(Font.SANS_SERIF, Font.BOLD, LABEL_POINTS)
    val metrics = g.fontMetrics

    // Biggest region first, so where two labels cannot both fit the one with more ground keeps its name.
    // Two hundred regions on one picture overlap heavily otherwise, and overlapping text is worse than
    // absent text: a name half over another name is unreadable and hides a second one that was fine.
    val visible = indices.toHashSet()
      .mapNotNull { regions.regions.getOrNull(it) }
      .sortedByDescending { it.cellCount }

    val placed = ArrayList<Rectangle>()

    for (place in visible) {
      val x = view.screenX(place.centre.x)
      val y = view.screenY(place.centre.y)
      if (x < 0 || y < 0 || x >= view.widthPx || y >= view.heightPx) continue

      val text = "${place.name} (${place.kind})"
      val width = metrics.stringWidth(text)
      if (width > view.widthPx / 3) continue

      val box = Rectangle(
        (x - width / 2).toInt() - 2,
        y.toInt() - metrics.ascent,
        width + 4,
        metrics.height
      )
      if (placed.any { it.intersects(box) }) continue
      placed.add(box)

      // Behind the text rather than an outline on it: an outline at eleven points over a busy tint is
      // unreadable, and the partition below is what the tint is for anyway.
      g.color = LABEL_BACKGROUND
      g.fillRect(box.x, box.y, box.width, box.height)
      g.color = LABEL
      g.drawString(text, box.x + 2, y.toInt())
    }

    return placed.size
  }

  /**
   * A repeating hue ramp, offset for water.
   *
   * Adjacent indices are adjacent in the growth order, not in space, so a simple ramp already gives
   * neighbours different colours most of the time - and where it does not, the border line is the thing
   * carrying the information.
   */
  private fun colorOf(region: Int, isWater: Boolean): Color {
    val hue = (region * GOLDEN_ANGLE) % 1.0f
    val saturation = if (isWater) 0.55f else 0.35f
    val brightness = if (isWater) 0.55f else 0.80f
    return Color(Color.HSBtoRGB(hue, saturation, brightness))
  }

  /** What was actually drawn, so a caller can say so rather than trusting that anything was. */
  class Stats(val regionsOnScreen: Int, val borderEdges: Int, val labels: Int) {

    override fun toString(): String {
      return "$regionsOnScreen region(s), $labels label(s)"
    }

    companion object {
      val NONE = Stats(0, 0, 0)
    }
  }

  private companion object {
    /** Successive multiples land far apart on the hue circle, so nearby indices do not look alike. */
    const val GOLDEN_ANGLE = 0.381966f

    const val LABEL_POINTS = 11

    const val BORDER_WIDTH = 1.6f

    val BORDER = Color(20, 20, 25, 220)
    val LABEL = Color(250, 250, 250)
    val LABEL_BACKGROUND = Color(0, 0, 0, 150)
  }
}
