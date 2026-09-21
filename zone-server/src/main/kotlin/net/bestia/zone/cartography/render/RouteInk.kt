package net.bestia.zone.cartography.render

import net.bestia.worldgen.civ.BridgeChannels
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import kotlin.math.max

/**
 * Roads, bridges and sea lanes: the dashed lines that turn a terrain map into a map of a settled country.
 *
 * Drawn as dashes rather than solid strokes, which is the convention that keeps a road distinguishable from a
 * river at a glance when both are one pixel wide and neither has room for a colour to help.
 *
 * ### Road class has to be inferred
 *
 * The generator classifies routes into track, road and highway - `civ/SettlementStage.RoadTier` - and then
 * throws the classification away: the enum is `private` and the tier is not written to any station channel.
 * What survives is `half_width`, which the tiers set to 1.6, 3.0 and 11.0 metres respectively, so the class is
 * recoverable from the geometry even though it is not recorded. [TRACK_MAX_HALF_WIDTH] and
 * [ROAD_MAX_HALF_WIDTH] sit between those figures rather than on them, so the inference survives a retune of
 * the road profile that does not reorder the tiers.
 */
object RouteInk {

  fun draw(g: Graphics2D, view: Viewport, inputs: TileInputs, palette: AtlasPalette) {
    if (!MapVisibility.draws(FeatureKind.ROAD, view.metresPerPixel)) return

    val features = inputs.featuresIn(view.bounds.expanded(view.metresPerPixel * MARGIN_PIXELS))

    for (feature in features) {
      when (feature) {
        // Two arms because a bridge is the one route that is not a centreline: the generator stores a
        // deck as a centre, a bearing and a span, so it arrives as a point marker.
        is PointMarker -> if (feature.kind == FeatureKind.BRIDGE) bridge(g, view, feature, palette)

        is PolylineFeature -> when (feature.kind) {
          FeatureKind.ROAD -> road(g, view, feature, palette)
          FeatureKind.SEA_LANE -> seaLane(g, view, feature, palette)
          else -> Unit
        }

        else -> Unit
      }
    }
  }

  private fun road(g: Graphics2D, view: Viewport, feature: PolylineFeature, palette: AtlasPalette) {
    val halfWidth = halfWidthOf(feature)
    val weight = when {
      halfWidth <= TRACK_MAX_HALF_WIDTH -> TRACK_PIXELS
      halfWidth <= ROAD_MAX_HALF_WIDTH -> ROAD_PIXELS
      else -> HIGHWAY_PIXELS
    }

    // A track is drawn with shorter dashes as well as a thinner pen, so the three classes stay apart even
    // where the pen widths round to the same number of pixels.
    val dash = if (halfWidth <= TRACK_MAX_HALF_WIDTH) TRACK_DASH else ROAD_DASH

    g.color = WaterInk.rgba(palette.roadInk, ROAD_ALPHA)
    g.stroke = BasicStroke(
      weight, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1.0f, dash, 0.0f
    )
    g.draw(pathOf(view, feature))
  }

  /**
   * A bridge is the one part of a route drawn solid and heavier: it is a structure, not a way.
   *
   * The deck is struck along the road's own bearing, so it reads as a short bar across the water rather
   * than as a mark beside it. [MIN_BRIDGE_PIXELS] is what keeps it legible once a span of tens of metres
   * is under a pixel - without it a bridge would be drawn at every zoom this band allows and visible at
   * almost none of them.
   */
  private fun bridge(g: Graphics2D, view: Viewport, marker: PointMarker, palette: AtlasPalette) {
    val bearingX = marker.optionalAttribute(BridgeChannels.BEARING_X) ?: return
    val bearingY = marker.optionalAttribute(BridgeChannels.BEARING_Y) ?: return
    val span = marker.optionalAttribute(BridgeChannels.SPAN) ?: return
    val half = max(span * 0.5, MIN_BRIDGE_PIXELS * 0.5 * view.metresPerPixel)

    g.color = WaterInk.rgba(palette.ink, BRIDGE_ALPHA)
    g.stroke = BasicStroke(BRIDGE_PIXELS, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
    g.draw(
      Line2D.Double(
        view.screenX(marker.position.x - bearingX * half),
        view.screenY(marker.position.y - bearingY * half),
        view.screenX(marker.position.x + bearingX * half),
        view.screenY(marker.position.y + bearingY * half)
      )
    )
  }

  /** Dotted, and in the water ink, because it is a route over water rather than a thing built on the ground. */
  private fun seaLane(g: Graphics2D, view: Viewport, feature: PolylineFeature, palette: AtlasPalette) {
    g.color = WaterInk.rgba(palette.waterInk, SEA_LANE_ALPHA)
    g.stroke = BasicStroke(
      SEA_LANE_PIXELS, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f, SEA_LANE_DASH, 0.0f
    )
    g.draw(pathOf(view, feature))
  }

  private fun halfWidthOf(feature: PolylineFeature): Double {
    val channel = feature.stations.channel(Profiles.CHANNEL_HALF_WIDTH)
    if (channel < 0) return 0.0

    // Midway along, so a road that widens as it nears a town is classified by what it mostly is.
    return feature.stations.sample(channel, 0.5)
  }

  private fun pathOf(view: Viewport, feature: PolylineFeature): Path2D.Double {
    val path = Path2D.Double()
    val points = feature.centerline.points

    for ((i, p) in points.withIndex()) {
      val x = view.screenX(p.x)
      val y = view.screenY(p.y)
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    return path
  }

  private const val MARGIN_PIXELS = 8.0

  /** Between the generator's 1.6 and 3.0 metre half-widths, and between 3.0 and 11.0. */
  private const val TRACK_MAX_HALF_WIDTH = 2.2
  private const val ROAD_MAX_HALF_WIDTH = 6.5

  private const val TRACK_PIXELS = 0.7f
  private const val ROAD_PIXELS = 1.0f
  private const val HIGHWAY_PIXELS = 1.5f
  private const val ROAD_ALPHA = 0.8

  private val TRACK_DASH = floatArrayOf(1.6f, 2.0f)
  private val ROAD_DASH = floatArrayOf(3.4f, 2.2f)

  private const val BRIDGE_PIXELS = 1.9f
  private const val BRIDGE_ALPHA = 0.85

  /** Shortest a deck may be drawn, so a real span of 30 m is still a bar and not a dot. */
  private const val MIN_BRIDGE_PIXELS = 5.0

  private const val SEA_LANE_PIXELS = 0.7f
  private const val SEA_LANE_ALPHA = 0.4
  private val SEA_LANE_DASH = floatArrayOf(0.8f, 3.2f)
}
