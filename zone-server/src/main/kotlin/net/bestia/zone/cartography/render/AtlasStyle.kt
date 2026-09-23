package net.bestia.zone.cartography.render

import net.bestia.worldgen.render.Colors
import net.bestia.worldgen.render.Viewport
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.math.pow

/**
 * The world drawn as a fantasy atlas: parchment, banded relief, hatched coasts, symbols for country.
 *
 * The style for every zoom at which a settlement is a dot rather than a place. Its whole argument is that a
 * map at this scale should be *symbolic*: a range of hills is a row of drawn hills, not a shaded slope, and
 * a wood is a scatter of drawn trees, not a green area. That is a claim about legibility rather than
 * nostalgia - at 128 metres per pixel a forest is four pixels wide, and four green pixels say far less than
 * one recognisable tree.
 *
 * ### Pass order, and why it is this order
 *
 * ```
 * 1  ground      biome-stained land, depth-graded water            per pixel
 * 2  parchment   paper mottling and fibre, over land and sea alike per pixel, paper space
 * 3  coast       shore stroke and the ruled lines outside it       per pixel
 * 4  glyphs      ranges, woods, wetlands, dunes                    vector, world-seeded
 * 5  water       rivers and lake edges                             vector
 * 6  routes      roads, bridges, sea lanes                         vector
 * 7  places      settlements and sites, then names                 vector
 * ```
 *
 * Every per-pixel pass runs before every vector pass, so ink is never mottled by paper laid over it.
 *
 * ### Relief is drawn, never shaded
 *
 * There was a fourth per-pixel pass between the paper and the coast: a quantised hillshade with hatching in
 * its darkest band. It is gone, and the reason is the same claim the rest of the style rests on. A shaded
 * slope is a *photograph* of terrain no matter how few steps it is quantised to, and next to a drawn peak it
 * reads as a smudge the peak is standing on. Worse, its band edges sit close together, so a broad gentle
 * hillside lands wholly inside one band and comes out as a shape with no internal structure at all.
 *
 * Steep ground now earns a **symbol** instead, whether or not it is a crest - see `GlyphScatter.reliefKind`.
 * That is the reference plates' own answer: they carry no shading anywhere, and everything the reader learns
 * about the shape of the land is carried by drawn marks.
 *
 * Within the vector passes the order is a hierarchy of what a reader is looking for. Glyphs are scenery and go
 * underneath; a river or a road is something you trace with a finger, so it must not be interrupted by a wood
 * drawn over it - which is what happens if the scatter runs last, and is why it does not. Places are last
 * because a settlement is the thing a road leads to, and its name is the last mark of all.
 */
class AtlasStyle(
  private val palette: AtlasPalette = AtlasPalette.VIVID,
  private val paperStrength: Double = 1.0,

  /** Whether grassland, steppe and scree carry marks of their own. See `GlyphScatter.scatter`. */
  private val openGround: Boolean = true
) : MapStyle {

  override val version: Int = VERSION

  override fun render(view: Viewport, inputs: TileInputs): BufferedImage {
    val image = BufferedImage(view.widthPx, view.heightPx, BufferedImage.TYPE_INT_RGB)
    val pixels = (image.raster.dataBuffer as DataBufferInt).data

    val terrain = TerrainRaster.sample(view, inputs, palette)

    ground(pixels, view, terrain)
    Parchment.apply(pixels, view, inputs.seed, paperStrength)
    Coastline.apply(pixels, view, terrain, palette)

    // Everything above wrote pixels directly; everything below is drawn. Taking the Graphics2D only now is
    // what guarantees the order the class comment describes rather than merely documenting it.
    val g = image.createGraphics()
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

      glyphs(g, view, inputs)
      WaterInk.draw(g, view, inputs, palette.water, palette.waterInk)
      RouteInk.draw(g, view, inputs, palette)
      PlaceInk.draw(g, view, inputs, palette)
    } finally {
      g.dispose()
    }

    return image
  }

  /**
   * Relief symbols, then cover on top of them.
   *
   * Two scatters rather than one, because a wooded hill is a hill with trees on it - see [GlyphKind]. Relief
   * goes first so a tree stands in front of the slope it grows on.
   */
  private fun glyphs(g: Graphics2D, view: Viewport, inputs: TileInputs) {
    val spread = reliefSpread(view)

    val relief = GlyphScatter.scatter(
      view, inputs, GlyphKind.Family.RELIEF,
      RELIEF_SPACING_PIXELS * spread, RELIEF_SIZE_PIXELS * spread.pow(RELIEF_SIZE_GAIN)
    )

    for (glyph in relief) {
      Glyphs.draw(g, glyph, palette)
    }

    // The ranges are handed to the cover pass as ground already taken. Drawing relief first no longer keeps a
    // tree behind the slope it grows on now that both are opaque - it puts the wood squarely over the peaks -
    // so the separation has to happen in the placement rather than in the order.
    val taken = relief.map {
      doubleArrayOf(it.worldX, it.worldY, it.size * RELIEF_CLEARANCE * view.metresPerPixel)
    }

    for (glyph in GlyphScatter.scatter(
      view, inputs, GlyphKind.Family.COVER, COVER_SPACING_PIXELS, COVER_SIZE_PIXELS, openGround, taken
    )) {
      Glyphs.draw(g, glyph, palette)
    }
  }

  /**
   * How much further apart relief symbols stand than at [REFERENCE_METRES_PER_PIXEL], as you zoom in.
   *
   * The lattice is pitched in pixels so that density stays constant across zooms, which is right for cover and
   * wrong for relief. A hillside is one landform however close you stand to it, so holding the pitch constant
   * means drawing it with four peaks at country scale and forty at approach scale - and forty peaks on one
   * hillside is the rash that buries the roads and woods under it.
   *
   * Widening the pitch instead is what map generalisation does in the other direction: fewer, larger symbols
   * for the same ground as the scale grows. Clamped at one below the reference, because a world view wants the
   * density it already has, and at [MAX_RELIEF_SPREAD] above it, because past that a range stops being a chain.
   */
  private fun reliefSpread(view: Viewport): Double =
    (REFERENCE_METRES_PER_PIXEL / view.metresPerPixel).pow(RELIEF_SPREAD_EXPONENT)
      .coerceIn(1.0, MAX_RELIEF_SPREAD)

  /** Land tone where dry, water graded by depth where wet, void outside the world. */
  private fun ground(pixels: IntArray, view: Viewport, terrain: TerrainRaster) {
    for (py in 0 until view.heightPx) {
      for (px in 0 until view.widthPx) {
        val t = terrain.index(px, py)
        val i = py * view.widthPx + px

        val depth = terrain.shore[t]
        pixels[i] = when {
          terrain.ground[t].isNaN() -> palette.paper
          depth > 0.0 -> Colors.mix(
            palette.water, palette.waterDeep, (depth / FULL_DEPTH_TONE_METRES).coerceAtMost(1.0)
          )

          else -> terrain.landTone[t]
        }
      }
    }
  }


  companion object {

    /**
     * Bumped whenever any pass changes what it draws.
     *
     * Part of the tile cache key. Without it a change in here serves whatever was baked under the previous
     * look, and a half-restyled map is indistinguishable from a rendering bug.
     */
    const val VERSION = 4

    /**
     * Lattice pitch and symbol size in pixels, per family.
     *
     * In pixels rather than metres so density and size stay constant across zooms - see [GlyphScatter]. Cover
     * is pitched tighter and drawn smaller than relief because a wood is many small things and a range is a
     * few large ones, and reversing that reads as scrub on flat ground.
     */
    private const val RELIEF_SPACING_PIXELS = 25.0
    private const val RELIEF_SIZE_PIXELS = 7.5

    /**
     * The zoom [reliefSpread] measures from, and how fast it opens out.
     *
     * The exponent is well under one: a full power would double the spacing every level and empty the map two
     * levels in. A symbol grows as the lattice opens, by [RELIEF_SIZE_GAIN], so the ground keeps roughly the
     * same weight of ink while the count of marks falls.
     */
    private const val REFERENCE_METRES_PER_PIXEL = 128.0
    private const val RELIEF_SPREAD_EXPONENT = 0.35
    private const val MAX_RELIEF_SPREAD = 3.0
    private const val RELIEF_SIZE_GAIN = 0.55
    private const val COVER_SPACING_PIXELS = 6.5
    private const val COVER_SIZE_PIXELS = 3.8

    /** How much ground a drawn range keeps clear of trees, as a multiple of each peak's own half-width. */
    private const val RELIEF_CLEARANCE = 1.15

    /** Water depth at which the tone stops darkening. Shelf and abyss should differ; 4 km of it need not. */
    private const val FULL_DEPTH_TONE_METRES = 400.0
  }
}
