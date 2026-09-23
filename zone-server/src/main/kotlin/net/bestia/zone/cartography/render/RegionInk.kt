package net.bestia.zone.cartography.render

import net.bestia.worldgen.place.PlaceRegion
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.vector.Vec2d
import java.awt.Font
import java.awt.Graphics2D
import kotlin.math.sqrt

/**
 * The names of the country itself: the wide, spaced capitals an atlas lays across a region.
 *
 * ### Names, and deliberately no borders
 *
 * `place/PlaceRegions` divides the world into named areas, and its own KDoc records that its borders are
 * **never rendered to a player** - the only renderer that draws them is the generator's own overlay, for
 * judging the cost field. Nothing here changes that. A region boundary is a quantised artefact of a cost
 * walk and drawing it would state a precision the data does not have, whereas a name laid across the middle
 * of an area claims only what it can support: that this stretch of country is called that.
 *
 * It is also what the reference plates do. Mirkwood has no outline; it has letters spread across it.
 *
 * ### Why the letters are spaced by hand
 *
 * Tracking is most of what separates a map label from a caption. Java2D has no letter-spacing attribute on a
 * plain font, so each character is drawn at its own advance plus a share of the size. The cost is that the
 * string is measured a character at a time, which at a few dozen labels a tile is nothing.
 *
 * ### Drawn for the plate, not for a served tile
 *
 * Gated on [TileInputs.regions] being present, which only the offline tool populates. A served tile leaves
 * every name to the client for the same reason it leaves settlement names: generated names are English by
 * construction and the client owns the localisation path.
 */
object RegionInk {

  fun draw(g: Graphics2D, view: Viewport, inputs: TileInputs) {
    val regions = inputs.regions ?: return

    for (placed in chosen(g, view, inputs, regions.regions)) {
      val x = view.screenX(placed.region.centre.x)
      val y = view.screenY(placed.region.centre.y)
      if (x < -OFF_TILE_PIXELS || y < -OFF_TILE_PIXELS) continue
      if (x > view.widthPx + OFF_TILE_PIXELS || y > view.heightPx + OFF_TILE_PIXELS) continue

      label(g, placed.region, x, y, placed.points)
    }
  }

  /**
   * Which names are drawn at this zoom, decided over the whole world rather than over the tile.
   *
   * Region names are long and set wide, so at any zoom where several are legible they collide - and unlike a
   * town symbol there is no sensible way to shrink one out of trouble. The largest region wins its ground and
   * the ones it covers are simply left out, which is what an atlas does: a sound is named and the three inlets
   * inside it are not, until you zoom to where there is room.
   *
   * Deciding it against every region in the world, not the ones in view, is what keeps two tiles agreeing. A
   * greedy pass over a tile's own subset would let a name that lost its ground on one tile win it on the next,
   * and the seam would fall through the middle of a word.
   */
  private fun chosen(
    g: Graphics2D,
    view: Viewport,
    inputs: TileInputs,
    regions: List<PlaceRegion>
  ): List<Placed> {
    val metresPerCell = inputs.config.baseResolution.metresPerCell
    val accepted = ArrayList<Placed>()

    // Biggest first, so the name of the country outranks the name of the corner of it.
    for (region in regions.sortedByDescending { it.cellCount }) {
      // Root of the cell count: a label wants the region's width, and the count is its area.
      val spanPixels = sqrt(region.cellCount.toDouble()) * metresPerCell / view.metresPerPixel
      if (spanPixels < MIN_SPAN_PIXELS) continue

      val points = (spanPixels * SIZE_SHARE).coerceIn(MIN_POINTS, MAX_POINTS)
      val candidate = Placed(region, points, extentOf(g, region, points, view.metresPerPixel))
      if (accepted.any { it.overlaps(candidate) }) continue

      accepted += candidate
    }

    return accepted
  }

  /** Half the width and half the height the label will occupy, in world metres. */
  private fun extentOf(g: Graphics2D, region: PlaceRegion, points: Double, metresPerPixel: Double): Vec2d {
    g.font = Font(Font.SERIF, Font.PLAIN, points.toInt().coerceAtLeast(1))

    val text = region.name.uppercase()
    val metrics = g.fontMetrics
    val width = text.sumOf { metrics.charWidth(it).toDouble() } + points * TRACKING * (text.length - 1)

    return Vec2d(
      (width / 2.0 + points * PADDING) * metresPerPixel,
      (points / 2.0 + points * PADDING) * metresPerPixel
    )
  }

  private class Placed(val region: PlaceRegion, val points: Double, val extent: Vec2d) {

    fun overlaps(other: Placed): Boolean {
      val dx = kotlin.math.abs(region.centre.x - other.region.centre.x)
      val dy = kotlin.math.abs(region.centre.y - other.region.centre.y)

      return dx < extent.x + other.extent.x && dy < extent.y + other.extent.y
    }
  }

  private fun label(g: Graphics2D, region: PlaceRegion, x: Double, y: Double, points: Double) {
    g.font = Font(Font.SERIF, Font.PLAIN, points.toInt().coerceAtLeast(1))

    val text = region.name.uppercase()
    val tracking = points * TRACKING
    val metrics = g.fontMetrics
    val width = text.sumOf { metrics.charWidth(it).toDouble() } + tracking * (text.length - 1)

    // A water name sits in its own ink, so a bay reads with the sea rather than with the land around it.
    val ink = if (region.isWater) WATER_INK else LAND_INK
    val halo = WaterInk.rgba(PAPER, HALO_ALPHA)

    var pen = x - width / 2.0
    for (character in text) {
      g.color = halo
      for (dx in -1..1) {
        for (dy in -1..1) {
          if (dx == 0 && dy == 0) continue
          g.drawString(character.toString(), (pen + dx).toFloat(), (y + dy).toFloat())
        }
      }

      g.color = WaterInk.rgba(ink, INK_ALPHA)
      g.drawString(character.toString(), pen.toFloat(), y.toFloat())

      pen += metrics.charWidth(character) + tracking
    }
  }

  /**
   * Narrowest a region may be on screen and still be named. Below it the letters cover the ground.
   *
   * Low enough that land regions qualify. They are far smaller than the sea areas around them - a few
   * kilometres against tens - so a threshold set where a sound looks right names nothing but water, which is
   * a map of the ocean with some country drawn in the gaps.
   */
  private const val MIN_SPAN_PIXELS = 52.0

  /** Clear space around a label, as a share of its point size, so two names never quite touch. */
  private const val PADDING = 0.6

  /** How far a label's centre may sit outside the tile and still be drawn, so a name is not clipped away. */
  private const val OFF_TILE_PIXELS = 220.0

  /** Point size as a share of the region's width, and the range it is held to. */
  private const val SIZE_SHARE = 0.11
  private const val MIN_POINTS = 9.0
  private const val MAX_POINTS = 26.0

  /** Extra advance per character, as a share of the point size. Generous: this is what says "map". */
  private const val TRACKING = 0.34

  private const val INK_ALPHA = 0.55
  private const val HALO_ALPHA = 0.5

  private val LAND_INK = net.bestia.worldgen.render.Colors.rgb(74, 60, 42)
  private val WATER_INK = net.bestia.worldgen.render.Colors.rgb(92, 106, 120)
  private val PAPER = net.bestia.worldgen.render.Colors.rgb(244, 238, 222)
}
