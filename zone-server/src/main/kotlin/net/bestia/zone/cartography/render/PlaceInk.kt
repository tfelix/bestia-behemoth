package net.bestia.zone.cartography.render

import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D

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
 * Not from the marker. A settlement's name lives as a 48-bit seed on its chronicle record, and rendering it
 * needs the culture off the `SETTLEMENT` marker as well - the same two-sided join `civ/SettlementSpawnPoints`
 * performs, for the same reason: placement knows the culture and history knows the name. A world with no
 * history simply has no names, which is why the lookup is nullable rather than defaulted.
 *
 * Generated names are English-only by construction. The localisation path is build-time Godot `tr()` CSVs, so
 * a per-world string can never enter it - see `world/SettlementLoreService`. That is also why labels are drawn
 * here only for the offline tool: a served tile leaves them to the client, which has the font.
 */
object PlaceInk {

  fun draw(g: Graphics2D, view: Viewport, inputs: TileInputs, palette: AtlasPalette) {
    val features = inputs.featuresIn(view.bounds.expanded(view.metresPerPixel * MARGIN_PIXELS))
    val labels = ArrayList<Label>()

    for (feature in features) {
      if (feature !is PointMarker) continue
      if (!MapVisibility.draws(feature.kind, view.metresPerPixel)) continue

      val x = view.screenX(feature.position.x)
      val y = view.screenY(feature.position.y)

      if (feature.kind == FeatureKind.SETTLEMENT) {
        val tier = tierOf(feature) ?: continue
        if (view.metresPerPixel > tier.visibleTo) continue

        settlement(g, x, y, tier, palette)
        nameOf(inputs, feature)?.let { labels += Label(it, x, y, tier) }
      } else {
        site(g, x, y, feature.kind, palette)
      }
    }

    if (inputs.labels) {
      // After every symbol, so a name is never drawn under the next town's mark.
      labels.sortedByDescending { it.tier.ordinal }.forEach { label(g, it, palette) }
    }
  }

  /**
   * City: a double ring with a keep. Town: a ring. Village: a filled dot. Hamlet: a small open dot.
   *
   * All of them centred on the marker and all of them the same size regardless of zoom, so a chain of towns
   * along a road reads as a chain rather than as dots of drifting weight.
   */
  private fun settlement(g: Graphics2D, x: Double, y: Double, tier: SettlementTier, palette: AtlasPalette) {
    g.color = WaterInk.rgba(palette.ink, SYMBOL_ALPHA)
    g.stroke = BasicStroke(SYMBOL_PEN, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

    when (tier) {
      SettlementTier.CITY -> {
        g.fill(dot(x, y, CITY_RADIUS * 0.45))
        g.draw(circle(x, y, CITY_RADIUS))
        g.draw(circle(x, y, CITY_RADIUS * 0.62))
      }

      SettlementTier.TOWN -> {
        g.fill(dot(x, y, TOWN_RADIUS * 0.4))
        g.draw(circle(x, y, TOWN_RADIUS))
      }

      SettlementTier.VILLAGE -> g.fill(dot(x, y, VILLAGE_RADIUS))

      SettlementTier.HAMLET -> g.draw(circle(x, y, HAMLET_RADIUS))
    }
  }

  /** Ruins get a broken square, tombs a barrow arc, everything else a small cross. */
  private fun site(g: Graphics2D, x: Double, y: Double, kind: FeatureKind, palette: AtlasPalette) {
    g.color = WaterInk.rgba(palette.ink, SITE_ALPHA)
    g.stroke = BasicStroke(SITE_PEN, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val r = SITE_RADIUS

    when (kind) {
      FeatureKind.RUIN, FeatureKind.ASH_RUIN -> {
        // Three sides of a square: the convention for a wall that no longer stands all the way round.
        val broken = Path2D.Double()
        broken.moveTo(x - r, y + r)
        broken.lineTo(x - r, y - r)
        broken.lineTo(x + r, y - r)
        broken.lineTo(x + r, y + r * 0.1)
        g.draw(broken)
      }

      FeatureKind.TOMB -> {
        val barrow = Path2D.Double()
        barrow.moveTo(x - r, y + r * 0.6)
        barrow.curveTo(x - r * 0.5, y - r, x + r * 0.5, y - r, x + r, y + r * 0.6)
        g.draw(barrow)
      }

      FeatureKind.FORT, FeatureKind.MONASTERY -> g.draw(Rectangle2D.Double(x - r, y - r, r * 2, r * 2))

      else -> {
        g.draw(Line2D.Double(x - r, y, x + r, y))
        g.draw(Line2D.Double(x, y - r, x, y + r))
      }
    }
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

  private fun tierOf(marker: PointMarker): SettlementTier? {
    val ordinal = marker.optionalAttribute(SettlementChannels.TIER)?.toInt() ?: return null
    return SettlementTier.entries.getOrNull(ordinal)
  }

  private fun nameOf(inputs: TileInputs, marker: PointMarker): String? {
    val index = marker.optionalAttribute(SettlementChannels.INDEX)?.toInt() ?: return null
    val culture = marker.optionalAttribute(SettlementChannels.CULTURE)?.toInt() ?: return null
    val record = inputs.chronicle.settlements.getOrNull(index) ?: return null
    if (record.nameSeed == 0L) return null

    return Names.place(record.nameSeed, culture)
  }

  private fun dot(x: Double, y: Double, r: Double) = Ellipse2D.Double(x - r, y - r, r * 2, r * 2)

  private fun circle(x: Double, y: Double, r: Double) = Ellipse2D.Double(x - r, y - r, r * 2, r * 2)

  private class Label(val text: String, val x: Double, val y: Double, val tier: SettlementTier)

  /**
   * Coarsest zoom each tier survives to, in metres per pixel.
   *
   * A world map that marked every hamlet would be a map of dots. Cities and towns carry the shape of a
   * country, so they stay at every zoom; the smaller two appear as you come in.
   */
  private val SettlementTier.visibleTo: Double
    get() = when (this) {
      SettlementTier.CITY -> Double.MAX_VALUE
      SettlementTier.TOWN -> Double.MAX_VALUE
      SettlementTier.VILLAGE -> 96.0
      SettlementTier.HAMLET -> 40.0
    }

  private val SettlementTier.labelPoints: Int
    get() = when (this) {
      SettlementTier.CITY -> 13
      SettlementTier.TOWN -> 11
      SettlementTier.VILLAGE -> 9
      SettlementTier.HAMLET -> 8
    }

  private val SettlementTier.labelOffset: Double
    get() = when (this) {
      SettlementTier.CITY -> CITY_RADIUS + 4.0
      SettlementTier.TOWN -> TOWN_RADIUS + 4.0
      else -> VILLAGE_RADIUS + 4.0
    }

  private const val MARGIN_PIXELS = 40.0

  private const val CITY_RADIUS = 4.6
  private const val TOWN_RADIUS = 3.2
  private const val VILLAGE_RADIUS = 1.7
  private const val HAMLET_RADIUS = 1.5

  private const val SYMBOL_PEN = 1.0f
  private const val SYMBOL_ALPHA = 0.92

  private const val SITE_RADIUS = 2.3
  private const val SITE_PEN = 0.85f
  private const val SITE_ALPHA = 0.7

  private const val LABEL_ALPHA = 0.95
  private const val LABEL_HALO_ALPHA = 0.75
}
