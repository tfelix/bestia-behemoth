package net.bestia.zone.cartography.tools

import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.render.Viewport
import net.bestia.zone.cartography.render.AtlasPalette
import net.bestia.zone.cartography.render.AtlasStyle
import net.bestia.zone.cartography.render.MapStyle
import net.bestia.zone.cartography.render.PlanStyle
import net.bestia.zone.cartography.render.TileInputs
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.pow

/**
 * Renders one view of the map to a PNG. The tight loop for working on how the map looks.
 *
 * Deliberately Spring-free and database-free: it builds the world straight from `StandardWorld.build` and
 * draws it. A 128 km world generates in about a second, so the whole cycle from editing a palette to looking
 * at the result is a few seconds - which is the difference between tuning a style and guessing at one.
 *
 * ```
 * ./gradlew :zone-server:mapRender -Pgenesis -Plevel=7
 * ./gradlew :zone-server:mapRender -Pgenesis -Plevel=3 -Px=64000 -Py=64000
 * ./gradlew :zone-server:mapRender -Pgenesis -Plevel=7 -Ppalette=mono
 * ```
 *
 * Labels are drawn here and not in a served tile. A served tile leaves names to the client, which has the
 * font and the player's own view scale; a tool that left them out would be unreadable for the one job it has.
 */
object MapRenderMain {

  private const val LEVEL = "--level"
  private const val X = "--x"
  private const val Y = "--y"
  private const val WIDTH = "--width"
  private const val HEIGHT = "--height"
  private const val PALETTE = "--palette"
  private const val PAPER = "--paper"
  private const val OUT = "--out"
  private const val NO_LABELS = "--no-labels"
  private const val STYLE = "--style"

  private val FLAGS = setOf(LEVEL, X, Y, WIDTH, HEIGHT, PALETTE, PAPER, OUT, NO_LABELS, STYLE)

  @JvmStatic
  fun main(argv: Array<String>) {
    val args = MapToolArgs.parse(argv, FLAGS)
    val config = args.config

    val width = args.int(WIDTH, DEFAULT_WIDTH)
    val height = args.int(HEIGHT, DEFAULT_HEIGHT)
    val level = args.int(LEVEL, defaultLevelFor(config.widthMetres, width))
    val metresPerPixel = 2.0.pow(level)

    val view = Viewport(
      centerX = args.double(X, config.widthMetres / 2.0),
      centerY = args.double(Y, config.heightMetres / 2.0),
      metresPerPixel = metresPerPixel,
      widthPx = width,
      heightPx = height
    )

    println(
      "world %.0f x %.0f km, seed %d, params %s".format(
        Locale.ROOT, config.widthMetres / 1000.0, config.heightMetres / 1000.0, config.seed, args.paramsOrigin
      )
    )

    val startedAt = System.nanoTime()
    val generated = StandardWorld.build(config, params = args.params)
    val generatedAt = System.nanoTime()

    val style: MapStyle = styleFor(args, metresPerPixel)

    val inputs = TileInputs.of(generated, labels = !args.has(NO_LABELS))
    val image = style.render(view, inputs)
    val renderedAt = System.nanoTime()

    val out = args.file(OUT, "build/map/render-L%02d.png".format(Locale.ROOT, level))
    out.parentFile?.mkdirs()
    ImageIO.write(image, "png", out)

    println(
      "L%d, %.1f m/px, %dx%d px, %.0f x %.0f km in view".format(
        Locale.ROOT, level, metresPerPixel, width, height,
        width * metresPerPixel / 1000.0, height * metresPerPixel / 1000.0
      )
    )
    println(
      "generate %d ms, render %d ms, %d kB -> %s".format(
        Locale.ROOT,
        (generatedAt - startedAt) / 1_000_000,
        (renderedAt - generatedAt) / 1_000_000,
        out.length() / 1024,
        out.absolutePath
      )
    )
  }

  /**
   * Which style to draw with: whatever `--style` says, or the one the zoom calls for.
   *
   * Defaulting by zoom rather than to the atlas, so `-Plevel=1` shows a town plan without having to be told -
   * which is what the tile service will do too, so the tool shows what the server would serve.
   */
  private fun styleFor(args: MapToolArgs, metresPerPixel: Double): MapStyle {
    val atlas = AtlasStyle(
      palette = AtlasPalette.byName(args.string(PALETTE) ?: "parchment"),
      paperStrength = args.double(PAPER, 1.0)
    )

    return when (args.string(STYLE) ?: if (metresPerPixel <= PlanStyle.MAX_METRES_PER_PIXEL) "plan" else "atlas") {
      "atlas" -> atlas
      "plan" -> PlanStyle()
      else -> throw IllegalArgumentException("Unknown style '${args.string(STYLE)}', expected atlas or plan")
    }
  }

  /** The level at which the whole world just fits the requested width - the view worth opening on. */
  private fun defaultLevelFor(worldMetres: Double, widthPx: Int): Int {
    var level = 0
    while (widthPx * 2.0.pow(level) < worldMetres) level++
    return level
  }

  private const val DEFAULT_WIDTH = 1024
  private const val DEFAULT_HEIGHT = 768
}
