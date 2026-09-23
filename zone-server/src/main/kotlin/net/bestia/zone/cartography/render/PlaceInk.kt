package net.bestia.zone.cartography.render

import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.cartography.render.PlaceNames.visibleTo
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Settlements, ruins and landmarks, with their names.
 *
 * ### A symbol per tier, not a dot scaled by population
 *
 * `viewer/MapRenderer` sizes a settlement dot by `sqrt(population)`, which is right for a diagnostic view -
 * you can read the population off it. A drawn map does the opposite: it gives a city a different *symbol* from
 * a hamlet, because a reader is asking "what kind of place is this" rather than "how many people". Four tiers,
 * four marks, and the mark is the same size at every zoom.
 *
 * ### Where a name comes from
 *
 * [PlaceNames], which is also what `MapTileService.features` asks. It used to be private here, and moved out
 * the moment a second caller existed: two copies of "which channel holds the name seed" would drift, and a
 * town labelled one thing on the atlas and another in the client is not an error any build catches.
 *
 * Generated names are English-only by construction. The localisation path is build-time Godot `tr()` CSVs, so
 * a per-world string can never enter it - see `world/SettlementLoreService`. That is also why labels are drawn
 * here only for the offline tool: a served tile leaves them to the client, which has the font.
 *
 * Note that this draws a name for settlements only, while [PlaceNames.nameOf] answers for built sites too.
 * That is not an oversight to fix by symmetry: a ruin's name belongs beside its symbol at the zoom a player
 * reads it at, and the atlas plate this renders is a world map where those labels would be a thicket. The
 * client labels them instead, over this same ink.
 */
object PlaceInk {

  fun draw(g: Graphics2D, view: Viewport, inputs: TileInputs, palette: AtlasPalette) {
    val features = inputs.featuresIn(view.bounds.expanded(view.metresPerPixel * MARGIN_PIXELS))

    val towns = ArrayList<TownMark>()
    val sites = ArrayList<SiteMark>()

    for (feature in features) {
      if (feature !is PointMarker) continue
      if (!MapVisibility.draws(feature.kind, view.metresPerPixel)) continue
      if (!MapVisibility.of(feature.kind).isPlace) continue
      if (isDrowned(inputs, feature)) continue

      val x = view.screenX(feature.position.x)
      val y = view.screenY(feature.position.y)

      if (feature.kind == FeatureKind.SETTLEMENT) {
        val tier = PlaceNames.tierOf(feature) ?: continue
        if (view.metresPerPixel > tier.visibleTo) continue
        if (!PlaceNames.wasFounded(inputs.chronicle, feature)) continue

        towns += TownMark(x, y, tier, PlaceNames.nameOf(inputs.chronicle, feature))
      } else {
        sites += SiteMark(x, y, feature.kind)
      }
    }

    for ((index, mark) in sites.withIndex()) {
      if (isCrowded(mark, index, sites, towns)) continue

      site(g, mark.x, mark.y, mark.kind, palette)
    }

    // Settlements over the sites, and names over everything: the town is what the reader came to find.
    for (town in towns) {
      settlement(g, town.x, town.y, town.tier, palette)
    }

    if (inputs.labels) {
      towns.mapNotNull { town -> town.name?.let { Label(it, town.x, town.y, town.tier) } }
        .sortedByDescending { it.tier.ordinal }
        .forEach { label(g, it, palette) }
    }
  }

  /**
   * Whether a site symbol would sit on something more important, and should be left off entirely.
   *
   * Dropped rather than nudged aside, because a moved symbol is a lie about where the thing is, and dropped
   * rather than drawn under, because a tomb across a city's towers costs the reader the city and tells them
   * nothing about the tomb. It resolves itself as the map is zoomed: these are pixel distances between fixed
   * world positions, so the same pair that collides at world scale has room two levels in and both appear.
   *
   * Settlements are never tested against anything - they always win. Between two sites the earlier in
   * `featuresIn` order wins, which is `(priority, id)` and therefore stable. Both comparisons are pairwise
   * against the full candidate list rather than against what survived, so a tile edge cannot change the
   * answer for a symbol standing near it.
   */
  private fun isCrowded(mark: SiteMark, index: Int, sites: List<SiteMark>, towns: List<TownMark>): Boolean {
    for (town in towns) {
      if (overlaps(mark.x, mark.y, SITE_RADIUS, town.x, town.y, town.tier.radius)) return true
    }

    for (earlier in 0 until index) {
      val rival = sites[earlier]
      if (overlaps(mark.x, mark.y, SITE_RADIUS, rival.x, rival.y, SITE_RADIUS)) return true
    }

    return false
  }

  private fun overlaps(
    ax: Double,
    ay: Double,
    ar: Double,
    bx: Double,
    by: Double,
    br: Double
  ): Boolean {
    val reach = (ar + br) * CROWDING
    val dx = ax - bx
    val dy = ay - by

    return dx * dx + dy * dy < reach * reach
  }

  private class SiteMark(val x: Double, val y: Double, val kind: FeatureKind)

  private class TownMark(val x: Double, val y: Double, val tier: SettlementTier, val name: String?)

  /**
   * Whether the place stands under water, and so is not a place anyone has seen.
   *
   * Volcanism is the case that forces this. Hotspots are placed on the crust rather than on the land, so a
   * world has seamounts as well as mountains, and the vent symbol was being drawn in open ocean hundreds of
   * metres above its own cone - which reads as a mistake rather than as a seamount, because a map at this
   * scale has no way to say "this is below you".
   *
   * Applied to every kind rather than to volcanism alone. [MapVisibility] decides what a player may learn;
   * this decides what there is to see, and nothing submerged qualifies whatever kind it is.
   */
  private fun isDrowned(inputs: TileInputs, marker: PointMarker): Boolean {
    val elevation = inputs.elevation
    val metresPerCell = elevation.region.resolution.metresPerCell
    val cellX = floor(marker.position.x / metresPerCell).toInt()
    val cellY = floor(marker.position.y / metresPerCell).toInt()
    if (!elevation.region.contains(cellX, cellY)) return true

    val ground = elevation.sampleBicubic(marker.position.x, marker.position.y)
    return ground.isNaN() || ground < inputs.seaLevel
  }

  /**
   * City: a walled block under three towers. Town: a tower between wall stubs. Village: a house. Hamlet: a dot.
   *
   * All of them the same size at every zoom, so a chain of towns along a road reads as a chain rather than as
   * marks of drifting weight. The hamlet is the only tier left abstract, because at four pixels a house and a
   * circle are the same handful of pixels and the circle is the honest one.
   */
  internal fun settlement(g: Graphics2D, x: Double, y: Double, tier: SettlementTier, palette: AtlasPalette) {
    val r = tier.radius

    when (tier) {
      SettlementTier.CITY -> {
        val body = Path2D.Double()
        body.append(Rectangle2D.Double(x - r * 0.9, y + r * 0.05, r * 1.8, r * 0.7), false)
        body.append(Rectangle2D.Double(x - r * 0.78, y - r * 0.5, r * 0.44, r * 0.6), false)
        body.append(Rectangle2D.Double(x - r * 0.22, y - r * 0.82, r * 0.44, r * 0.9), false)
        body.append(Rectangle2D.Double(x + r * 0.34, y - r * 0.5, r * 0.44, r * 0.6), false)
        stamp(g, palette, SYMBOL_PEN, body, emptyList())
      }

      // A tower under a conical roof. The roof is what tells it from a fort, which is squat and battlemented:
      // two rectangles of different proportions are not two symbols at eleven pixels.
      SettlementTier.TOWN -> {
        val foot = y + r * 0.72
        val eaves = y - r * 0.25

        val body = Path2D.Double()
        body.moveTo(x - r * 0.34, foot)
        body.lineTo(x - r * 0.34, eaves)
        body.lineTo(x - r * 0.6, eaves)
        body.lineTo(x, y - r * 0.95)
        body.lineTo(x + r * 0.6, eaves)
        body.lineTo(x + r * 0.34, eaves)
        body.lineTo(x + r * 0.34, foot)
        body.closePath()

        stamp(
          g, palette, SYMBOL_PEN, body,
          listOf(
            Line2D.Double(x - r * 0.95, foot, x - r * 0.34, foot),
            Line2D.Double(x + r * 0.34, foot, x + r * 0.95, foot)
          )
        )
      }

      SettlementTier.VILLAGE -> stamp(g, palette, SYMBOL_PEN, house(x, y, r), emptyList())

      SettlementTier.HAMLET -> stamp(
        g, palette, SYMBOL_PEN, null,
        listOf(Ellipse2D.Double(x - r * 0.85, y - r * 0.85, r * 1.7, r * 1.7))
      )
    }
  }

  /**
   * The symbol for a place that is not a settlement.
   *
   * Every kind that can reach here has its own mark, because that is the whole job of a symbol: a reader
   * should know a mine from a monument without consulting a legend. They divide into four families that share
   * a visual language - works still standing are built shapes, remains are broken ones, hazards are
   * irregular, and the two generic marks are stars.
   *
   * The `else` arm is a safety net rather than a decision. [MapVisibility.of] is the exhaustive `when` that
   * forces a new feature kind to be thought about before it can be drawn at all; a kind that gets as far as
   * here without a symbol is one somebody let into the place band and has not finished.
   */
  internal fun site(g: Graphics2D, x: Double, y: Double, kind: FeatureKind, palette: AtlasPalette) {
    val r = SITE_RADIUS

    when (kind) {
      FeatureKind.FORT -> fort(g, x, y, r, palette)
      FeatureKind.MONASTERY -> monastery(g, x, y, r, palette)
      FeatureKind.LIGHTHOUSE -> lighthouse(g, x, y, r, palette)
      FeatureKind.ROADSIDE_INN -> inn(g, x, y, r, palette)
      FeatureKind.MINE -> mine(g, x, y, r, palette)
      FeatureKind.MONUMENT -> obelisk(g, x, y, r, palette)

      FeatureKind.RUIN -> ruin(g, x, y, r, palette, ash = false)
      FeatureKind.ASH_RUIN -> ruin(g, x, y, r, palette, ash = true)
      FeatureKind.TOMB -> headstone(g, x, y, r, palette)
      FeatureKind.BATTLEFIELD -> crossedSwords(g, x, y, r, palette)

      FeatureKind.VOLCANIC_VENT -> volcano(g, x, y, r, palette)
      FeatureKind.LAVA_POOL -> lavaPool(g, x, y, r, palette)
      FeatureKind.WOUND -> wound(g, x, y, r, palette)
      FeatureKind.SHRINE -> dolmen(g, x, y, r, palette)

      else -> star(g, x, y, r, palette)
    }
  }

  /** A squat tower with a notch bitten out of its battlements. */
  private fun fort(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    val foot = y + r * 0.85
    val top = y - r * 0.85

    val body = Path2D.Double()
    body.moveTo(x - r * 0.62, foot)
    body.lineTo(x - r * 0.62, top)
    body.lineTo(x - r * 0.24, top)
    body.lineTo(x - r * 0.24, top + r * 0.32)
    body.lineTo(x + r * 0.24, top + r * 0.32)
    body.lineTo(x + r * 0.24, top)
    body.lineTo(x + r * 0.62, top)
    body.lineTo(x + r * 0.62, foot)
    body.closePath()

    stamp(g, palette, SITE_PEN, body, emptyList())
  }

  /** A narrow hall under a spire. */
  private fun monastery(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    val foot = y + r * 0.85

    val body = Path2D.Double()
    body.moveTo(x - r * 0.85, foot)
    body.lineTo(x - r * 0.85, y - r * 0.05)
    body.lineTo(x - r * 0.35, y - r * 0.05)
    body.lineTo(x - r * 0.35, y - r * 0.45)
    body.lineTo(x - r * 0.08, y - r * 0.95)
    body.lineTo(x + r * 0.2, y - r * 0.45)
    body.lineTo(x + r * 0.2, y - r * 0.05)
    body.lineTo(x + r * 0.85, y - r * 0.05)
    body.lineTo(x + r * 0.85, foot)
    body.closePath()

    stamp(g, palette, SITE_PEN, body, listOf(Line2D.Double(x - r * 0.08, y - r * 0.95, x - r * 0.08, foot)))
  }

  /** A slim tower with a lamp box on top, throwing two rays. */
  private fun lighthouse(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    val foot = y + r * 0.85
    val lamp = y - r * 0.6

    val body = Path2D.Double()
    body.moveTo(x - r * 0.42, foot)
    body.lineTo(x - r * 0.2, lamp)
    body.lineTo(x + r * 0.2, lamp)
    body.lineTo(x + r * 0.42, foot)
    body.closePath()
    body.append(Rectangle2D.Double(x - r * 0.3, lamp - r * 0.34, r * 0.6, r * 0.34), false)

    // Two lines a side, spreading: a beam has width, and a single ray each way reads as a windmill sail.
    val glass = lamp - r * 0.17

    stamp(
      g, palette, SITE_PEN, body,
      listOf(
        Line2D.Double(x - r * 0.34, glass, x - r * 1.1, glass - r * 0.38),
        Line2D.Double(x - r * 0.34, glass, x - r * 1.1, glass + r * 0.3),
        Line2D.Double(x + r * 0.34, glass, x + r * 1.1, glass - r * 0.38),
        Line2D.Double(x + r * 0.34, glass, x + r * 1.1, glass + r * 0.3)
      )
    )
  }

  /** A house with a sign hanging from a post: the mark for somewhere to stop. */
  private fun inn(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    val foot = y + r * 0.85

    val body = house(x - r * 0.25, y, r * 0.85)
    body.append(Rectangle2D.Double(x + r * 0.55, y - r * 0.35, r * 0.5, r * 0.34), false)

    stamp(g, palette, SITE_PEN, body, listOf(Line2D.Double(x + r * 0.8, y - r * 0.35, x + r * 0.8, foot)))
  }

  /** Pick and hammer crossed, which is the chart symbol for a working. */
  private fun mine(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    val hammer = Path2D.Double()
    hammer.moveTo(x - r * 0.7, y + r * 0.7)
    hammer.lineTo(x + r * 0.55, y - r * 0.55)

    val head = Line2D.Double(x + r * 0.3, y - r * 0.8, x + r * 0.8, y - r * 0.3)

    val pick = Path2D.Double()
    pick.moveTo(x + r * 0.7, y + r * 0.7)
    pick.lineTo(x - r * 0.55, y - r * 0.55)

    val blade = Path2D.Double()
    blade.moveTo(x - r * 0.9, y - r * 0.28)
    blade.quadTo(x - r * 0.55, y - r * 0.95, x - r * 0.18, y - r * 0.62)

    stamp(g, palette, SITE_PEN, null, listOf(hammer, head, pick, blade))
  }

  /** A standing shaft on a stepped base. */
  private fun obelisk(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    val foot = y + r * 0.85

    val body = Path2D.Double()
    body.append(Rectangle2D.Double(x - r * 0.6, foot - r * 0.28, r * 1.2, r * 0.28), false)
    body.moveTo(x - r * 0.22, foot - r * 0.28)
    body.lineTo(x - r * 0.12, y - r * 0.85)
    body.lineTo(x + r * 0.12, y - r * 0.85)
    body.lineTo(x + r * 0.22, foot - r * 0.28)
    body.closePath()

    stamp(g, palette, SITE_PEN, body, emptyList())
  }

  /**
   * Two wall stubs with the building gone from between them, optionally under falling ash.
   *
   * One function for both because an ash ruin *is* a ruin - it is the same broken wall, and drawing it as an
   * unrelated symbol would lose the only thing a reader needs to know first.
   */
  private fun ruin(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette, ash: Boolean) {
    val foot = y + r * 0.85

    val left = Path2D.Double()
    left.moveTo(x - r * 0.72, foot)
    left.lineTo(x - r * 0.72, y - r * 0.55)
    left.lineTo(x - r * 0.38, y - r * 0.18)
    left.lineTo(x - r * 0.38, foot)

    val right = Path2D.Double()
    right.moveTo(x + r * 0.28, foot)
    right.lineTo(x + r * 0.28, y - r * 0.05)
    right.lineTo(x + r * 0.7, y - r * 0.5)
    right.lineTo(x + r * 0.7, foot)

    val marks = mutableListOf<Shape>(left, right, Line2D.Double(x - r * 0.9, foot, x + r * 0.9, foot))
    if (ash) {
      marks += Ellipse2D.Double(x - r * 0.5, y - r * 1.05, r * 0.2, r * 0.2)
      marks += Ellipse2D.Double(x, y - r * 0.85, r * 0.2, r * 0.2)
      marks += Ellipse2D.Double(x + r * 0.45, y - r * 1.15, r * 0.2, r * 0.2)
    }

    stamp(g, palette, SITE_PEN, null, marks)
  }

  /**
   * A capstone on two uprights: standing stones, which is what a shrine is on this map.
   *
   * It was drawn for a tomb and moved here, because three heavy stones say "somebody raised this and people
   * still come to it" far better than they say "somebody is buried under it". A grave wants a grave marker,
   * which is what [headstone] now is.
   */
  private fun dolmen(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    val foot = y + r * 0.8
    val lintel = y - r * 0.3

    val body = Path2D.Double()
    body.append(Rectangle2D.Double(x - r * 0.9, lintel - r * 0.32, r * 1.8, r * 0.32), false)
    body.append(Rectangle2D.Double(x - r * 0.62, lintel, r * 0.32, foot - lintel), false)
    body.append(Rectangle2D.Double(x + r * 0.3, lintel, r * 0.32, foot - lintel), false)

    stamp(g, palette, SITE_PEN, body, listOf(Line2D.Double(x - r * 0.95, foot, x + r * 0.95, foot)))
  }

  /** Two swords crossed, hilts down: the oldest mark on any map for a field somebody died on. */
  private fun crossedSwords(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    stamp(
      g, palette, SITE_PEN, null,
      listOf(
        Line2D.Double(x - r * 0.8, y + r * 0.8, x + r * 0.75, y - r * 0.75),
        Line2D.Double(x + r * 0.8, y + r * 0.8, x - r * 0.75, y - r * 0.75),
        Line2D.Double(x - r * 0.78, y + r * 0.3, x - r * 0.28, y + r * 0.8),
        Line2D.Double(x + r * 0.78, y + r * 0.3, x + r * 0.28, y + r * 0.8)
      )
    )
  }

  /** A cone with its crater notched out, and a plume leaning off the top. */
  private fun volcano(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    val foot = y + r * 0.8

    val body = Path2D.Double()
    body.moveTo(x - r * 0.95, foot)
    body.lineTo(x - r * 0.32, y - r * 0.62)
    body.lineTo(x - r * 0.12, y - r * 0.42)
    body.lineTo(x + r * 0.12, y - r * 0.42)
    body.lineTo(x + r * 0.32, y - r * 0.62)
    body.lineTo(x + r * 0.95, foot)
    body.closePath()

    val plume = Path2D.Double()
    plume.moveTo(x, y - r * 0.5)
    plume.quadTo(x + r * 0.45, y - r * 0.95, x - r * 0.1, y - r * 1.35)

    stamp(g, palette, SITE_PEN, body, listOf(plume))
  }

  /**
   * Three flames standing in a shallow pool.
   *
   * The pool used to be the symbol and the flames an afterthought on top of it, which read as a puddle -
   * nothing about a lobed outline says molten. Reversing the two fixes it: the flames are the shape, drawn
   * large and to a point and at three different heights, and the pool is a single line they rise out of.
   */
  private fun lavaPool(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    val surface = y + r * 0.6

    val flames = Path2D.Double()
    flames.append(tongue(x - r * 0.48, surface, r * 0.95), false)
    flames.append(tongue(x + r * 0.05, surface, r * 1.45), false)
    flames.append(tongue(x + r * 0.55, surface, r * 0.8), false)

    val pool = Path2D.Double()
    pool.moveTo(x - r * 0.95, surface)
    pool.quadTo(x, surface + r * 0.4, x + r * 0.95, surface)

    stamp(g, palette, SITE_PEN, flames, listOf(pool))
  }

  /**
   * A flame licking upward, drawn to a point.
   *
   * The tip has to be sharp and the two tongues have to differ in height. A pair of matched rounded lobes on
   * top of a blob does not read as fire at all - it reads as ears.
   */
  private fun tongue(x: Double, y: Double, r: Double): Path2D.Double {
    val flame = Path2D.Double()
    flame.moveTo(x, y)
    flame.quadTo(x + r * 0.32, y - r * 0.36, x + r * 0.03, y - r * 0.95)
    flame.quadTo(x - r * 0.28, y - r * 0.42, x, y)
    flame.closePath()

    return flame
  }

  /** A jagged star, deliberately unlike anything else on the sheet: nothing natural looks like this. */
  private fun wound(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    // A closed, irregular silhouette rather than crossed lines. Drawn as lines it was another asterisk, and
    // the map already has one of those in [star] - two marks that differ only in how many arms they have are
    // one mark as far as a reader is concerned.
    val reach = doubleArrayOf(1.0, 0.34, 0.72, 0.3, 0.92, 0.36, 0.66, 0.28, 0.85, 0.32)

    val body = Path2D.Double()
    for (i in reach.indices) {
      val angle = i * Math.PI * 2.0 / reach.size - Math.PI / 2.0
      val px = x + cos(angle) * r * reach[i]
      val py = y + sin(angle) * r * reach[i]
      if (i == 0) body.moveTo(px, py) else body.lineTo(px, py)
    }
    body.closePath()

    stamp(g, palette, SITE_PEN, body, emptyList())
  }

  /** A headstone with a rounded top, standing in its own ground: the mark for a grave. */
  private fun headstone(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    val foot = y + r * 0.8
    val shoulder = y - r * 0.25

    val body = Path2D.Double()
    body.moveTo(x - r * 0.5, foot)
    body.lineTo(x - r * 0.5, shoulder)
    body.quadTo(x - r * 0.5, y - r * 0.85, x, y - r * 0.85)
    body.quadTo(x + r * 0.5, y - r * 0.85, x + r * 0.5, shoulder)
    body.lineTo(x + r * 0.5, foot)
    body.closePath()

    stamp(
      g, palette, SITE_PEN, body,
      listOf(
        Line2D.Double(x - r * 0.26, y - r * 0.2, x + r * 0.26, y - r * 0.2),
        Line2D.Double(x - r * 0.26, y + r * 0.1, x + r * 0.26, y + r * 0.1),
        Line2D.Double(x - r * 0.95, foot, x + r * 0.95, foot)
      )
    )
  }

  /** Six points, the generic mark for somewhere worth going that is none of the above. */
  private fun star(g: Graphics2D, x: Double, y: Double, r: Double, palette: AtlasPalette) {
    val marks = (0 until 3).map { i ->
      val angle = i * Math.PI / 3.0
      val dx = cos(angle) * r * 0.85
      val dy = sin(angle) * r * 0.85
      Line2D.Double(x - dx, y - dy, x + dx, y + dy) as Shape
    }

    stamp(g, palette, SITE_PEN, null, marks)
  }

  /** A pitched roof over a wall: the shape every built symbol here is a variation on. */
  private fun house(x: Double, y: Double, r: Double): Path2D.Double {
    val foot = y + r * 0.85

    val body = Path2D.Double()
    body.moveTo(x - r * 0.58, foot)
    body.lineTo(x - r * 0.58, y - r * 0.08)
    body.lineTo(x - r * 0.75, y - r * 0.08)
    body.lineTo(x, y - r * 0.78)
    body.lineTo(x + r * 0.75, y - r * 0.08)
    body.lineTo(x + r * 0.58, y - r * 0.08)
    body.lineTo(x + r * 0.58, foot)
    body.closePath()

    return body
  }

  /**
   * Draws a symbol twice: once broadly in paper, then in ink.
   *
   * The first pass is a halo, and it is what makes these legible at all now that the ground under them can be
   * a packed wood or a dark range. Without it a mine over a forest is a tangle of ink on ink, which is exactly
   * how the old marks failed - they were not too small so much as too easily lost.
   *
   * A [body] is a silhouette: filled with paper and then outlined, so the symbol sits on the sheet as a shape.
   * [detail] is everything drawn as a line. Passing a null body is normal - several of these symbols are
   * nothing but lines.
   */
  private fun stamp(
    g: Graphics2D,
    palette: AtlasPalette,
    pen: Float,
    body: Path2D.Double?,
    detail: List<Shape>
  ) {
    // The silhouette is filled opaque, not tinted: a symbol you can see the forest through is a symbol the
    // forest is drawn on top of, and at this size that costs the shape. The halo around it stays soft,
    // because its job is to lift the outline off whatever it crosses rather than to clear a hole in the map.
    body?.let {
      g.color = Color(palette.paper)
      g.fill(it)
    }

    g.color = WaterInk.rgba(palette.paper, HALO_ALPHA)
    g.stroke = BasicStroke(pen * HALO_GAIN, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    body?.let { g.draw(it) }
    detail.forEach { g.draw(it) }

    g.color = Color(palette.ink)
    g.stroke = BasicStroke(pen, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    body?.let { g.draw(it) }
    detail.forEach { g.draw(it) }
  }

  private fun label(g: Graphics2D, label: Label, palette: AtlasPalette) {
    g.font = Font(Font.SERIF, Font.PLAIN, label.tier.labelPoints)
    val metrics = g.fontMetrics
    val width = metrics.stringWidth(label.text)

    val x = label.x - width / 2.0
    val y = label.y - label.tier.labelOffset

    // A halo of paper behind the text, so a name over hatching or a forest stays readable without a box.
    g.color = WaterInk.rgba(palette.paper, LABEL_HALO_ALPHA)
    for (dx in -1..1) {
      for (dy in -1..1) {
        if (dx == 0 && dy == 0) continue
        g.drawString(label.text, (x + dx).toFloat(), (y + dy).toFloat())
      }
    }

    g.color = WaterInk.rgba(palette.ink, LABEL_ALPHA)
    g.drawString(label.text, x.toFloat(), y.toFloat())
  }

  private fun dot(x: Double, y: Double, r: Double) = Ellipse2D.Double(x - r, y - r, r * 2, r * 2)

  private fun circle(x: Double, y: Double, r: Double) = Ellipse2D.Double(x - r, y - r, r * 2, r * 2)

  private class Label(val text: String, val x: Double, val y: Double, val tier: SettlementTier)

  private val SettlementTier.labelPoints: Int
    get() = when (this) {
      SettlementTier.CITY -> 13
      SettlementTier.TOWN -> 11
      SettlementTier.VILLAGE -> 9
      SettlementTier.HAMLET -> 8
    }

  private val SettlementTier.labelOffset: Double
    get() = radius + 5.0

  /**
   * How far outside the tile features are gathered, in pixels.
   *
   * Comfortably wider than the widest pair [isCrowded] can compare - a city's ten pixels against a site's
   * six - because a symbol inside the tile must see every mark that could suppress it, including ones whose
   * own centres fall outside. Too narrow and a tomb beside a city would be drawn on one tile and dropped on
   * the next.
   */
  private const val MARGIN_PIXELS = 40.0

  /** How close two symbols may come, as a share of their combined radii, before the lesser is dropped. */
  private const val CROWDING = 1.05

  /**
   * Half-width of each tier's symbol, in pixels, at every zoom.
   *
   * Roughly twice what they were. The old marks were rings of two or three pixels, which is legible on bare
   * paper and invisible the moment a wood or a range is drawn under them - and both are drawn far more
   * densely now than when those numbers were chosen.
   */
  private val SettlementTier.radius: Double
    get() = when (this) {
      SettlementTier.CITY -> 10.0
      SettlementTier.TOWN -> 7.4
      SettlementTier.VILLAGE -> 5.7
      SettlementTier.HAMLET -> 3.3
    }

  private const val SYMBOL_PEN = 1.2f

  private const val SITE_RADIUS = 6.3
  private const val SITE_PEN = 1.3f

  /** The paper halo under every symbol: how opaque, and how much wider than the ink pen it is stroked. */
  private const val HALO_ALPHA = 0.72
  private const val HALO_GAIN = 2.4f

  private const val LABEL_ALPHA = 0.95
  private const val LABEL_HALO_ALPHA = 0.75
}
