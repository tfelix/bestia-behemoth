package net.bestia.zone.cartography.tools

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.zone.cartography.render.AtlasPalette
import net.bestia.zone.cartography.render.PlaceInk
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/**
 * Every place symbol on one sheet, at the size the map draws it.
 *
 * Exists because a symbol cannot be judged where it is used. A mine appears on perhaps three points of a whole
 * world and a lighthouse on none at all, so deciding whether the set reads as a set - whether a fort is told
 * apart from a monastery, whether anything is too faint at its real size - means hunting a map for marks that
 * may not be on it. This draws them side by side instead.
 *
 * It renders no world and reads no layers, so it costs nothing to run:
 *
 * ```
 * ./gradlew :zone-server:mapLegend
 * ```
 */
object MapLegendMain {

  private const val OUT = "--out"
  private const val SCALE = "--scale"
  private const val PALETTE = "--palette"

  @JvmStatic
  fun main(argv: Array<String>) {
    val args = MapToolArgs.parse(argv, setOf(OUT, SCALE, PALETTE))
    val scale = args.int(SCALE, 1)
    val palette = AtlasPalette.byName(args.string(PALETTE) ?: "vivid")

    val image = BufferedImage(SHEET_WIDTH * scale, SHEET_HEIGHT * scale, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    g.color = Color(palette.paper)
    g.fillRect(0, 0, image.width, image.height)
    g.scale(scale.toDouble(), scale.toDouble())

    // The symbol is drawn at its real pixel size and the sheet is magnified afterwards, so what is on show is
    // the mark the map makes rather than a larger redrawing of it.
    var index = 0
    for (tier in SettlementTier.entries) {
      cell(g, index++, tier.name.lowercase(Locale.ROOT)) { x, y ->
        PlaceInk.settlement(g, x, y, tier, palette)
      }
    }

    for (kind in SITES) {
      cell(g, index++, kind.name.lowercase(Locale.ROOT).replace('_', ' ')) { x, y ->
        PlaceInk.site(g, x, y, kind, palette)
      }
    }

    biomes(g, palette)

    g.dispose()

    val out = File(args.string(OUT) ?: "build/map/legend.png")
    out.parentFile?.mkdirs()
    ImageIO.write(image, "png", out)

    println("$index symbols and ${Biome.entries.size} biomes at ${scale}x -> ${out.absolutePath}")
  }

  /**
   * A swatch per biome, in the tone the map stains the ground with.
   *
   * Shown as the *finished* land tone rather than the raw entry in the table - what [AtlasPalette.biomeTone]
   * returns is pulled towards the bare land colour by `biomeStain` before any of it reaches a pixel, so a key
   * built from the raw values would be a key to colours the map never draws.
   *
   * Ocean and lake are here for completeness and are the two the map never stains onto land.
   */
  private fun biomes(g: java.awt.Graphics2D, palette: AtlasPalette) {
    val top = MARGIN + SYMBOL_ROWS * CELL_HEIGHT + SECTION_GAP

    for ((index, biome) in Biome.entries.withIndex()) {
      val column = index % COLUMNS
      val row = index / COLUMNS
      val x = MARGIN + column * CELL_WIDTH + (CELL_WIDTH - SWATCH_WIDTH) / 2.0
      val y = top + row * CELL_HEIGHT

      g.color = Color(palette.landTone(palette.biomeTone(biome), 0.0, 0.0))
      g.fillRect(x.toInt(), y.toInt(), SWATCH_WIDTH, SWATCH_HEIGHT)

      g.color = Color(palette.ink)
      g.drawRect(x.toInt(), y.toInt(), SWATCH_WIDTH, SWATCH_HEIGHT)

      g.font = Font(Font.SERIF, Font.PLAIN, CAPTION_POINTS)
      val caption = biome.name.lowercase(Locale.ROOT).replace('_', ' ')
      val width = g.fontMetrics.stringWidth(caption)
      g.drawString(
        caption,
        (MARGIN + column * CELL_WIDTH + CELL_WIDTH / 2.0 - width / 2.0).toFloat(),
        (y + SWATCH_HEIGHT + CAPTION_POINTS + 4).toFloat()
      )
    }
  }

  private fun cell(g: java.awt.Graphics2D, index: Int, caption: String, draw: (Double, Double) -> Unit) {
    val column = index % COLUMNS
    val row = index / COLUMNS
    val x = MARGIN + column * CELL_WIDTH + CELL_WIDTH / 2.0
    val y = MARGIN + row * CELL_HEIGHT + CELL_HEIGHT / 2.0 - CAPTION_DROP / 2.0

    draw(x, y)

    g.font = Font(Font.SERIF, Font.PLAIN, CAPTION_POINTS)
    g.color = Color(net.bestia.worldgen.render.Colors.rgb(70, 58, 44))
    val width = g.fontMetrics.stringWidth(caption)
    g.drawString(caption, (x - width / 2.0).toFloat(), (y + CAPTION_DROP).toFloat())
  }

  /** Every kind that can reach `PlaceInk.site`, in the order its families are described there. */
  private val SITES = listOf(
    FeatureKind.FORT,
    FeatureKind.MONASTERY,
    FeatureKind.LIGHTHOUSE,
    FeatureKind.ROADSIDE_INN,
    FeatureKind.MINE,
    FeatureKind.MONUMENT,
    FeatureKind.RUIN,
    FeatureKind.ASH_RUIN,
    FeatureKind.TOMB,
    FeatureKind.BATTLEFIELD,
    FeatureKind.VOLCANIC_VENT,
    FeatureKind.LAVA_POOL,
    FeatureKind.WOUND,
    FeatureKind.SHRINE,
    FeatureKind.POI
  )

  private const val COLUMNS = 5
  private const val CELL_WIDTH = 96
  private const val CELL_HEIGHT = 74
  private const val MARGIN = 16
  private const val CAPTION_POINTS = 10
  private const val CAPTION_DROP = 22.0

  private const val SYMBOL_ROWS = 4
  private const val SECTION_GAP = 24
  private const val SWATCH_WIDTH = 64
  private const val SWATCH_HEIGHT = 26

  private const val BIOME_ROWS = 5

  private const val SHEET_WIDTH = MARGIN * 2 + COLUMNS * CELL_WIDTH
  private const val SHEET_HEIGHT =
    MARGIN * 2 + SYMBOL_ROWS * CELL_HEIGHT + SECTION_GAP + BIOME_ROWS * CELL_HEIGHT
}
