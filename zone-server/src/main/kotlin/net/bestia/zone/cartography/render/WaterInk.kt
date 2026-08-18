package net.bestia.zone.cartography.render

import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import kotlin.math.sqrt

/**
 * Rivers and lakes, from their own geometry.
 *
 * This is the pass [TerrainRaster] defers to. Standing water above sea level is not in the shore field,
 * because approximating it from the kilometre `water_level` raster put a rectangular staircase around every
 * lake; here a lake is the `AreaFeature` the generator actually produced, with an exact ring, and a river is
 * its `PolylineFeature` centreline rather than a ridge of high flow accumulation guessed off a raster.
 *
 * ### Width comes from the station table, not from the zoom
 *
 * A river's drawn width is its real width in metres, converted to pixels, floored at [MIN_RIVER_PIXELS]. So a
 * trunk river is visibly heavier than a tributary at any zoom, and at world zoom - where every channel is far
 * under a pixel - they all collapse to the floor and the network reads as a network. Scaling the width by
 * zoom instead would make every river the same weight, which loses the one piece of hierarchy the map has.
 */
object WaterInk {

  fun draw(g: Graphics2D, view: Viewport, inputs: TileInputs, fill: Int, ink: Int) {
    val features = inputs.featuresIn(view.bounds.expanded(view.metresPerPixel * MARGIN_PIXELS))

    // Lakes first: a river runs into a lake, so the lake's fill must not cover the river's mouth.
    for (feature in features) {
      if (feature.kind != FeatureKind.LAKE && feature.kind != FeatureKind.OXBOW_LAKE) continue
      if (feature !is AreaFeature) continue
      if (!MapVisibility.draws(feature.kind, view.metresPerPixel)) continue

      lake(g, view, feature, fill, ink)
    }

    for (feature in features) {
      if (feature.kind != FeatureKind.RIVER_CHANNEL) continue
      if (feature !is PolylineFeature) continue
      if (!MapVisibility.draws(feature.kind, view.metresPerPixel)) continue

      river(g, view, feature, ink)
    }
  }

  private fun lake(g: Graphics2D, view: Viewport, feature: AreaFeature, fill: Int, ink: Int) {
    val path = Path2D.Double()
    val vertices = feature.ring.vertices
    if (vertices.isEmpty()) return

    for ((i, v) in vertices.withIndex()) {
      val x = view.screenX(v.x)
      val y = view.screenY(v.y)
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.closePath()

    g.color = rgba(fill, LAKE_FILL_ALPHA)
    g.fill(path)
    g.color = rgba(ink, LAKE_EDGE_ALPHA)
    g.stroke = BasicStroke(LAKE_EDGE_PIXELS, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    g.draw(path)
  }

  /**
   * A channel, tapering with its own width.
   *
   * Drawn as a run of segments each with its own stroke weight rather than as one path, because a river
   * widens by an order of magnitude between its head and its mouth and a single stroke can only have one
   * width. Round caps make the joins between weights invisible.
   */
  private fun river(g: Graphics2D, view: Viewport, feature: PolylineFeature, ink: Int) {
    val line = feature.centerline
    if (line.vertexCount < 2) return

    val widthChannel = feature.stations.channel(Profiles.CHANNEL_WIDTH)
    g.color = rgba(ink, RIVER_ALPHA)

    for (i in 0 until line.segmentCount) {
      val a = line.points[i]
      val b = line.points[i + 1]

      val u = if (line.segmentCount == 1) 0.5 else (i + 0.5) / line.segmentCount
      val metres = feature.stations.sample(widthChannel, u)
      val pixels = (metres / view.metresPerPixel).coerceAtLeast(MIN_RIVER_PIXELS)

      g.stroke = BasicStroke(pixels.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
      g.draw(
        Line2D.Double(
          view.screenX(a.x), view.screenY(a.y), view.screenX(b.x), view.screenY(b.y)
        )
      )
    }
  }

  /** Screen length of a polyline, for callers deciding whether a feature is worth drawing at all. */
  fun screenLength(view: Viewport, line: Polyline): Double {
    var total = 0.0
    for (i in 0 until line.segmentCount) {
      val a = line.points[i]
      val b = line.points[i + 1]
      val dx = (a.x - b.x) / view.metresPerPixel
      val dy = (a.y - b.y) / view.metresPerPixel
      total += sqrt(dx * dx + dy * dy)
    }
    return total
  }

  internal fun rgba(rgb: Int, alpha: Double) = Color(
    (rgb ushr 16) and 0xFF,
    (rgb ushr 8) and 0xFF,
    rgb and 0xFF,
    (alpha * 255).toInt().coerceIn(0, 255)
  )

  /**
   * How far outside the tile features are gathered, in pixels.
   *
   * A river whose centreline misses the tile can still put a stroke on it, and more importantly a feature's
   * bbox is already expanded by its influence radius, so the query is generous in the direction that matters.
   */
  private const val MARGIN_PIXELS = 8.0

  /** Floor on a river's drawn width. Below about a pixel a stroke stops being a continuous line. */
  private const val MIN_RIVER_PIXELS = 1.15
  private const val RIVER_ALPHA = 0.88

  private const val LAKE_FILL_ALPHA = 0.95
  private const val LAKE_EDGE_ALPHA = 0.8
  private const val LAKE_EDGE_PIXELS = 0.9f
}
