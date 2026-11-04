package net.bestia.zone.cartography.render

import net.bestia.worldgen.civ.BridgeChannels
import net.bestia.worldgen.civ.RoadClass
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.VectorFeature
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import kotlin.math.hypot
import kotlin.math.max

/**
 * Roads, bridges and sea lanes: the dashed lines that turn a terrain map into a map of a settled country.
 *
 * ### One style per road class
 *
 * - **Cobbled road**: a cased line - dark edges with paper showing down the middle - which is how a map says
 *   "built". It is the one road drawn without dashes, and the casing is what keeps it from reading as a river.
 * - **Dirt road**: a dashed line, the convention that keeps a way distinguishable from a river at a glance when
 *   both are one pixel wide and neither has room for a colour to help.
 * - **Trail**: a fine dotted line.
 *
 * ### The class is read, not inferred
 *
 * It used to be guessed from `half_width`, because the generator threw its classification away, and the
 * thresholds went stale when the widths were retuned: no road was ever inked as a track and almost every road as
 * a highway. The class is now stored on the road - [RoadClass.of] - and this reads it.
 */
object RouteInk {

  fun draw(g: Graphics2D, view: Viewport, inputs: TileInputs, palette: AtlasPalette) {
    if (!MapVisibility.draws(FeatureKind.ROAD, view.metresPerPixel)) return

    val features = inputs.featuresIn(view.bounds.expanded(view.metresPerPixel * MARGIN_PIXELS))

    roads(g, view, features, palette)

    for (feature in features) {
      when (feature) {
        // Two arms because a bridge is the one route that is not a centreline: the generator stores a
        // deck as a centre, a bearing and a span, so it arrives as a point marker. Drawn after the ways,
        // because a deck is a structure carrying one rather than a length of it.
        is PointMarker -> if (feature.kind == FeatureKind.BRIDGE) bridge(g, view, feature, palette)

        is PolylineFeature -> if (feature.kind == FeatureKind.SEA_LANE) seaLane(g, view, feature, palette)

        else -> Unit
      }
    }
  }

  /**
   * Every way in view, with each stretch of ground inked exactly once.
   *
   * ### Why the roads cannot simply be drawn one after another
   *
   * The trunk network is routed as shortest paths over a shared lattice, so several roads legitimately run
   * along the same ground for a while - a spur joins a road some way before the junction and they are one
   * way from there on. Stroking each feature independently draws that stretch two or three times, and two
   * things go wrong at once. The ink is laid over itself, so the shared length comes out darker than the
   * rest; and each stroke starts its dash pattern at its own beginning, so one road's gaps land on another
   * road's dashes and the stretch **fills in solid**. What should read as the busiest road on the map reads
   * instead as a river, which is the one thing the dashes exist to prevent.
   *
   * So a segment is claimed by the first way to use it and skipped by the rest. Heaviest first, so where a
   * trail runs along a cobbled road the ground is inked as the cobbled road rather than as whatever happened to be
   * sorted first.
   *
   * Two tiles agree about this without sharing anything: a way that shares a segment inside a tile must
   * itself pass through that tile, so the feature query returns it on both sides of any seam.
   */
  private fun roads(g: Graphics2D, view: Viewport, features: List<VectorFeature>, palette: AtlasPalette) {
    val claimed = HashSet<Long>()

    val ways = features
      .filterIsInstance<PolylineFeature>()
      .filter { it.kind == FeatureKind.ROAD }
      // Heaviest first; a road with no class (none the generator emits) is drawn as a trail, last.
      .sortedByDescending { RoadClass.of(it)?.ordinal ?: -1 }

    for (way in ways) {
      when (RoadClass.of(way) ?: RoadClass.TRAIL) {
        RoadClass.COBBLED -> {
          // Casing first, then the paper down the middle: two strokes over one claimed run, so the inner one
          // never shows where the casing was skipped.
          val runs = claimedRuns(view, way, claimed)
          g.color = WaterInk.rgba(palette.roadInk, CASING_ALPHA)
          for (run in runs) solid(g, run, COBBLED_CASING_PIXELS)
          g.color = WaterInk.rgba(palette.paper, 1.0)
          for (run in runs) solid(g, run, COBBLED_INNER_PIXELS)
        }

        RoadClass.DIRT_ROAD -> {
          g.color = WaterInk.rgba(palette.roadInk, ROAD_ALPHA)
          way(g, view, way, claimed, ROAD_PIXELS, ROAD_DASH, BasicStroke.CAP_BUTT)
        }

        // Round caps on a dash shorter than the pen is wide: a row of dots, which no other line on the map is.
        RoadClass.TRAIL -> {
          g.color = WaterInk.rgba(palette.roadInk, ROAD_ALPHA)
          way(g, view, way, claimed, TRAIL_PIXELS, TRAIL_DASH, BasicStroke.CAP_ROUND)
        }
      }
    }
  }

  /** The runs of a way no heavier way has claimed, as screen paths. [way]'s claiming without its dashes. */
  private fun claimedRuns(view: Viewport, feature: PolylineFeature, claimed: MutableSet<Long>): List<Path2D.Double> {
    val cell = max(view.metresPerPixel * CLAIM_PIXELS, MIN_CLAIM_METRES)
    val points = feature.centerline.points
    val out = ArrayList<Path2D.Double>()
    var run: Path2D.Double? = null

    for (i in 0 until points.size - 1) {
      val from = points[i]
      val to = points[i + 1]

      if (claim(claimed, cell, from.x, from.y, to.x, to.y)) {
        if (run == null) {
          run = Path2D.Double()
          run.moveTo(view.screenX(from.x), view.screenY(from.y))
          out.add(run)
        }
        run.lineTo(view.screenX(to.x), view.screenY(to.y))
      } else {
        run = null
      }
    }

    return out
  }

  private fun solid(g: Graphics2D, run: Path2D.Double, weight: Float) {
    g.stroke = BasicStroke(weight, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
    g.draw(run)
  }

  /**
   * Draws the parts of one way that no heavier way has already covered, with the dash running unbroken.
   *
   * Two things have to be right at once here, and getting either wrong turns a dashed road solid.
   *
   * **Which ground is left.** Ways that run together do not share vertices - they are routed separately and
   * resampled - so comparing vertices exactly finds only a few of the overlaps and leaves the rest drawn
   * twice, once over the other. The ground is claimed in cells a pixel or so across instead, which is the
   * scale at which "the same road" is a meaningful claim.
   *
   * **Where the dash is up to.** A broken way is several subpaths, and Java2D restarts the dash pattern at
   * every subpath - always at the start of an *ink* run. Where the breaks come thick and fast, every fragment
   * is shorter than one dash and the whole way inks solid. So each fragment is stroked with the phase the
   * way has actually travelled, and the dashes carry across the gaps as though nothing had interrupted them.
   */
  private fun way(
    g: Graphics2D,
    view: Viewport,
    feature: PolylineFeature,
    claimed: MutableSet<Long>,
    weight: Float,
    dash: FloatArray,
    cap: Int
  ) {
    val cell = max(view.metresPerPixel * CLAIM_PIXELS, MIN_CLAIM_METRES)
    val period = (dash[0] + dash[1]).toDouble()
    val points = feature.centerline.points

    var travelled = 0.0
    var phase = 0.0
    var run: Path2D.Double? = null

    for (i in 0 until points.size - 1) {
      val from = points[i]
      val to = points[i + 1]

      if (claim(claimed, cell, from.x, from.y, to.x, to.y)) {
        if (run == null) {
          run = Path2D.Double()
          run.moveTo(view.screenX(from.x), view.screenY(from.y))
          phase = travelled % period
        }
        run.lineTo(view.screenX(to.x), view.screenY(to.y))
      } else {
        stroke(g, run, weight, dash, phase, cap)
        run = null
      }

      travelled += hypot(to.x - from.x, to.y - from.y) / view.metresPerPixel
    }

    stroke(g, run, weight, dash, phase, cap)
  }

  private fun stroke(g: Graphics2D, run: Path2D.Double?, weight: Float, dash: FloatArray, phase: Double, cap: Int) {
    if (run == null) return

    g.stroke = BasicStroke(weight, cap, BasicStroke.JOIN_ROUND, 1.0f, dash, phase.toFloat())
    g.draw(run)
  }

  /**
   * Claims the ground a segment covers, or reports that something already has it.
   *
   * The midpoint decides, and the whole segment is then claimed: a segment is a pixel or two long at map
   * scale, so asking about its middle asks about all of it, and claiming every cell it crosses is what stops
   * a way that approaches at a slight angle from slipping between the cells of the one it is joining.
   */
  private fun claim(
    claimed: MutableSet<Long>,
    cell: Double,
    fromX: Double,
    fromY: Double,
    toX: Double,
    toY: Double
  ): Boolean {
    if (cellKey((fromX + toX) / 2.0, (fromY + toY) / 2.0, cell) in claimed) return false

    val steps = (hypot(toX - fromX, toY - fromY) / (cell * CLAIM_STEP)).toInt() + 1
    for (step in 0..steps) {
      val t = step.toDouble() / steps
      claimed += cellKey(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t, cell)
    }

    return true
  }

  private fun cellKey(x: Double, y: Double, cell: Double): Long =
    Math.round(x / cell) * 4_000_037L + Math.round(y / cell)

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

  /**
   * How coarsely the ground is claimed: in pixels, with a floor in metres for the closest zooms.
   *
   * A pixel or so, because that is the width of the line being drawn. Finer and two ways a few metres apart
   * both claim their own cells and both draw; coarser and a road erases a track running genuinely beside it.
   */
  private const val CLAIM_PIXELS = 1.4
  private const val MIN_CLAIM_METRES = 1.0

  /** How far apart a segment is sampled when claiming, in cells. Under one, or it claims a dotted line. */
  private const val CLAIM_STEP = 0.5

  /** A dirt road: the dashed line every class used to get. */
  private const val ROAD_PIXELS = 1.0f
  private const val ROAD_ALPHA = 0.8
  private val ROAD_DASH = floatArrayOf(3.4f, 2.2f)

  /**
   * A trail: dots, from a dash far shorter than the round cap the pen puts on each end. A full pixel wide
   * rather than the 0.7 a track used to get, because a dot that thin antialiases away to nothing.
   */
  private const val TRAIL_PIXELS = 1.0f
  private val TRAIL_DASH = floatArrayOf(0.2f, 2.4f)

  /** A cobbled road: the dark casing, and the paper stroke inside it that makes it a double line. */
  private const val COBBLED_CASING_PIXELS = 2.4f
  private const val COBBLED_INNER_PIXELS = 1.0f
  private const val CASING_ALPHA = 0.9

  private const val BRIDGE_PIXELS = 2.6f
  private const val BRIDGE_ALPHA = 0.9

  /** Shortest a deck may be drawn, so a real span of 30 m is still a bar and not a dot. */
  private const val MIN_BRIDGE_PIXELS = 7.5

  private const val SEA_LANE_PIXELS = 0.7f
  private const val SEA_LANE_ALPHA = 0.4
  private val SEA_LANE_DASH = floatArrayOf(0.8f, 3.2f)
}
