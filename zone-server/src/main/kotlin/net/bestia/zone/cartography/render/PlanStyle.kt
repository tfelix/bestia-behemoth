package net.bestia.zone.cartography.render

import net.bestia.worldgen.civ.DistrictChannels
import net.bestia.worldgen.civ.DistrictKind
import net.bestia.worldgen.render.Colors
import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.VectorFeature
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.awt.geom.Path2D

/**
 * A settlement from above: quarters, streets, roofs, walls. The minimap's style.
 *
 * Where [AtlasStyle] is symbolic - a mountain is a drawn mountain - this is literal. Every building is its own
 * footprint at its own bearing, every street its own centreline, because at these zooms the reader is not
 * asking "what kind of country is this" but "where am I and what is that building". A symbol would be a lie
 * about geometry the generator actually produced.
 *
 * ### Streets are drawn as gaps, not as lines
 *
 * The pass order is quarter fills, then street surfaces, then roofs. A street is a *pale wide stroke* laid over
 * the quarter fill, which leaves the buildings sitting in the gaps between strokes rather than beside drawn
 * lines. That is how the reference plate reads and it is also self-correcting: the generator lays buildings
 * along streets, so the two fit together without this style having to know how they were placed.
 *
 * ### What it does when there is no town
 *
 * Falls through to open ground with the atlas's own relief and coastline under it. A plan tile over wilderness
 * is a legitimate request - the minimap follows the player wherever they walk - and it must not come out blank.
 */
class PlanStyle(
  private val palette: PlanPalette = PlanPalette.SLATE,
  private val atlas: AtlasPalette = AtlasPalette.PARCHMENT
) : MapStyle {

  override val version: Int = VERSION

  override fun render(view: Viewport, inputs: TileInputs): BufferedImage {
    val image = BufferedImage(view.widthPx, view.heightPx, BufferedImage.TYPE_INT_RGB)
    val pixels = (image.raster.dataBuffer as DataBufferInt).data

    val terrain = TerrainRaster.sample(view, inputs, atlas)

    ground(pixels, view, terrain)
    InkRelief.apply(pixels, view, terrain, atlas, inputs.seed, DetailRelief.of(view, inputs, terrain), hatch = false)
    Coastline.apply(pixels, view, terrain, atlas)

    val g = image.createGraphics()
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

      val features = inputs.featuresIn(view.bounds.expanded(view.metresPerPixel * MARGIN_PIXELS))

      districts(g, view, features)
      WaterInk.draw(g, view, inputs, palette.water, palette.waterEdge)
      streets(g, view, features)
      buildings(g, view, features)
      walls(g, view, features)
    } finally {
      g.dispose()
    }

    return image
  }

  /** Open country, in a flatter tone than the atlas uses: a plan's ground is a backdrop, not a subject. */
  private fun ground(pixels: IntArray, view: Viewport, terrain: TerrainRaster) {
    for (py in 0 until view.heightPx) {
      for (px in 0 until view.widthPx) {
        val t = terrain.index(px, py)
        val i = py * view.widthPx + px

        pixels[i] = when {
          terrain.ground[t].isNaN() -> palette.ground
          terrain.shore[t] > 0.0 -> palette.water
          else -> Colors.mix(palette.ground, terrain.landTone[t], GROUND_BIOME_SHARE)
        }
      }
    }
  }

  private fun districts(g: Graphics2D, view: Viewport, features: List<VectorFeature>) {
    for (feature in features) {
      if (feature.kind != FeatureKind.DISTRICT || feature !is AreaFeature) continue
      if (!MapVisibility.draws(feature.kind, view.metresPerPixel)) continue

      val kind = kindOf(feature) ?: continue
      g.color = WaterInk.rgba(palette.districtTone(kind), DISTRICT_ALPHA)
      g.fill(ringPath(view, feature))
    }
  }

  private fun streets(g: Graphics2D, view: Viewport, features: List<VectorFeature>) {
    g.color = WaterInk.rgba(palette.street, STREET_ALPHA)

    for (feature in features) {
      if (feature.kind != FeatureKind.STREET || feature !is PolylineFeature) continue
      if (!MapVisibility.draws(feature.kind, view.metresPerPixel)) continue

      val metres = halfWidthMetres(feature) * 2.0
      val pixels = (metres / view.metresPerPixel).coerceAtLeast(MIN_STREET_PIXELS)

      g.stroke = BasicStroke(pixels.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
      g.draw(polylinePath(view, feature))
    }
  }

  /**
   * Roofs, filled and outlined.
   *
   * The outline is drawn per building rather than once for all of them, because two adjacent buildings must
   * show the party wall between them - a single combined path would merge a terrace into one blob, which is the
   * difference between a street of houses and a warehouse.
   */
  private fun buildings(g: Graphics2D, view: Viewport, features: List<VectorFeature>) {
    val outlinePixels = OUTLINE_PIXELS.coerceAtMost((MAX_OUTLINE_SHARE / view.metresPerPixel).toFloat())

    for (feature in features) {
      if (feature.kind != FeatureKind.BUILDING || feature !is FootprintFeature) continue
      if (!MapVisibility.draws(feature.kind, view.metresPerPixel)) continue

      val path = cornersPath(view, feature)

      g.color = WaterInk.rgba(palette.districtTone(DistrictKind.RESIDENTIAL), ROOF_ALPHA)
      g.fill(path)

      if (outlinePixels > 0f) {
        g.color = WaterInk.rgba(palette.outline, OUTLINE_ALPHA)
        g.stroke = BasicStroke(outlinePixels, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        g.draw(path)
      }
    }
  }

  private fun walls(g: Graphics2D, view: Viewport, features: List<VectorFeature>) {
    g.color = WaterInk.rgba(palette.wall, WALL_ALPHA)

    for (feature in features) {
      if (!MapVisibility.draws(feature.kind, view.metresPerPixel)) continue

      when (feature.kind) {
        FeatureKind.TOWN_WALL -> {
          if (feature !is PolylineFeature) continue
          val metres = halfWidthMetres(feature) * 2.0
          val pixels = (metres / view.metresPerPixel).coerceAtLeast(MIN_WALL_PIXELS)
          g.stroke = BasicStroke(pixels.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
          g.draw(polylinePath(view, feature))
        }

        // A gate is a gap in the wall, so it is drawn as the wall's own tone across the opening rather than as
        // a symbol - the wall pass has already laid the circuit down and this breaks it.
        FeatureKind.GATE -> {
          if (feature !is FootprintFeature) continue
          g.color = WaterInk.rgba(palette.street, 1.0)
          g.fill(cornersPath(view, feature))
          g.color = WaterInk.rgba(palette.wall, WALL_ALPHA)
        }

        else -> Unit
      }
    }
  }

  private fun kindOf(feature: AreaFeature): DistrictKind? {
    val table = feature.perimeter ?: return null
    val channel = table.channelNames.indexOf(DistrictChannels.KIND)
    if (channel < 0) return null

    return DistrictKind.entries.getOrNull(table.valueAt(channel, 0).toInt())
  }

  private fun halfWidthMetres(feature: PolylineFeature): Double {
    val channel = feature.stations.channel(Profiles.CHANNEL_HALF_WIDTH)
    if (channel < 0) return 0.0
    return feature.stations.sample(channel, 0.5)
  }

  private fun ringPath(view: Viewport, feature: AreaFeature): Path2D.Double {
    val path = Path2D.Double()
    for ((i, v) in feature.ring.vertices.withIndex()) {
      val x = view.screenX(v.x)
      val y = view.screenY(v.y)
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.closePath()
    return path
  }

  private fun cornersPath(view: Viewport, feature: FootprintFeature): Path2D.Double {
    val path = Path2D.Double()
    for ((i, v) in feature.corners().withIndex()) {
      val x = view.screenX(v.x)
      val y = view.screenY(v.y)
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.closePath()
    return path
  }

  private fun polylinePath(view: Viewport, feature: PolylineFeature): Path2D.Double {
    val path = Path2D.Double()
    for ((i, p) in feature.centerline.points.withIndex()) {
      val x = view.screenX(p.x)
      val y = view.screenY(p.y)
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
  }


  companion object {

    /** Bumped whenever any pass changes what it draws. Part of the tile cache key; see [AtlasStyle.VERSION]. */
    const val VERSION = 1

    /**
     * Coarsest zoom the plan style is used at. Above this the atlas draws instead.
     *
     * Matched to `MapVisibility.TOWN_DETAIL`: there is no point choosing this style at a scale where every
     * feature it exists to draw is filtered out.
     */
    const val MAX_METRES_PER_PIXEL = 12.0

    private const val MARGIN_PIXELS = 24.0

    /** How much of the biome tint reaches a plan's open ground. Low: a plan is not a land-cover map. */
    private const val GROUND_BIOME_SHARE = 0.45

    private const val DISTRICT_ALPHA = 0.85
    private const val ROOF_ALPHA = 0.95
    private const val STREET_ALPHA = 0.9
    private const val WALL_ALPHA = 0.95
    private const val OUTLINE_ALPHA = 0.55

    private const val MIN_STREET_PIXELS = 1.4
    private const val MIN_WALL_PIXELS = 1.8

    /**
     * Building outlines, thinned out as the view pulls back and dropped entirely once they would be thicker
     * than the roofs they surround - past that point the outline is the only thing visible and a town reads as
     * a solid dark mass.
     */
    private const val OUTLINE_PIXELS = 0.7f
    private const val MAX_OUTLINE_SHARE = 4.0
  }
}
