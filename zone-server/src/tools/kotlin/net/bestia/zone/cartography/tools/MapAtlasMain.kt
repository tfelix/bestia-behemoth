package net.bestia.zone.cartography.tools

import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.render.Viewport
import net.bestia.zone.cartography.render.TileInputs
import net.bestia.zone.cartography.tile.TileId
import net.bestia.zone.cartography.tile.TileRenderer
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.pow

/**
 * Renders one whole zoom level twice - as a single frame and as assembled tiles - and compares them.
 *
 * ```
 * ./gradlew :zone-server:mapAtlas -Pgenesis -Plevel=6
 * ```
 *
 * ### Why both, and why the comparison is the point
 *
 * The single frame is what the map is *meant* to look like: one viewport, one render, no tiling anywhere, so it
 * cannot have a seam. The assembly is what a client actually sees. If the two differ, the difference is a
 * tiling artefact and its position on the page says which pass caused it - a grid of lines is paper or hatching
 * keyed to the frame, a dotted ring inside each tile edge is a halo too narrow, missing symbols along the edges
 * are a feature query margin too small.
 *
 * `AtlasStyleSeamTest` asserts the same property on four tiles and is what keeps it true. This exists for when
 * that test fails, or when a new pass needs checking over real ground at a real level rather than over a 128
 * pixel square: it writes the difference out so it can be looked at instead of reasoned about.
 */
object MapAtlasMain {

  private const val LEVEL = "--level"
  private const val OUT = "--out"
  private const val TILED = "--tiled"

  private val FLAGS = setOf(LEVEL, OUT, TILED)

  @JvmStatic
  fun main(argv: Array<String>) {
    val args = MapToolArgs.parse(argv, FLAGS)
    val config = args.config
    val fit = TileId.fitLevel(config.widthMetres)
    val level = args.int(LEVEL, fit - 1)

    val across = TileId.tilesAcross(config.widthMetres, level)
    val down = TileId.tilesAcross(config.heightMetres, level)
    val width = (across * TileId.TILE_PIXELS).toInt()
    val height = (down * TileId.TILE_PIXELS).toInt()

    require(width.toLong() * height <= MAX_PIXELS) {
      "L$level is ${width}x$height = ${width.toLong() * height / 1_000_000} Mpx, over the " +
          "${MAX_PIXELS / 1_000_000} Mpx cap. Pick a coarser level."
    }

    val generated = StandardWorld.build(config, params = args.params)
    val inputs = TileInputs.of(generated)
    val renderer = TileRenderer(inputs)
    val out = args.file(OUT, "build/map")
    out.mkdirs()

    println(
      "L%d, %.0f m/px, %d x %d tiles, %d x %d px".format(
        Locale.ROOT, level, 2.0.pow(level), across, down, width, height
      )
    )

    val tiled = assemble(renderer, level, across, down, width, height)
    write(out, "atlas-L%02d-tiled.png".format(level), tiled)

    if (args.has(TILED)) return

    val whole = single(renderer, level, width, height)
    write(out, "atlas-L%02d-whole.png".format(level), whole)
    compare(whole, tiled, out, level)
  }

  /** The level assembled from individually rendered tiles: what a client sees. */
  private fun assemble(
    renderer: TileRenderer,
    level: Int,
    across: Long,
    down: Long,
    width: Int,
    height: Int
  ): BufferedImage {
    val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val startedAt = System.nanoTime()

    for (ty in 0 until down) {
      for (tx in 0 until across) {
        val tile = renderer.render(TileId(level, tx, ty))

        // Tile ty counts north from the origin, canvas rows count south from the top.
        val originY = ((down - 1 - ty) * TileId.TILE_PIXELS).toInt()
        canvas.setRGB(
          (tx * TileId.TILE_PIXELS).toInt(), originY,
          TileId.TILE_PIXELS, TileId.TILE_PIXELS,
          (tile.raster.dataBuffer as DataBufferInt).data, 0, TileId.TILE_PIXELS
        )
      }
    }

    println("assembled %d tiles in %.1f s".format(Locale.ROOT, across * down, (System.nanoTime() - startedAt) / 1e9))
    return canvas
  }

  /** The same ground as one viewport: what the map is meant to look like. */
  private fun single(renderer: TileRenderer, level: Int, width: Int, height: Int): BufferedImage {
    val metresPerPixel = 2.0.pow(level)
    val view = Viewport(
      centerX = width * metresPerPixel / 2.0,
      centerY = height * metresPerPixel / 2.0,
      metresPerPixel = metresPerPixel,
      widthPx = width,
      heightPx = height
    )

    val startedAt = System.nanoTime()
    val image = renderer.styleFor(level).render(view, renderer.inputs)
    println("rendered one frame in %.1f s".format(Locale.ROOT, (System.nanoTime() - startedAt) / 1e9))
    return image
  }

  /**
   * Per-channel difference between the two, as a count and as an image.
   *
   * Written out only when something differs, so a clean run leaves no misleading file behind from a previous
   * dirty one. The image is the difference amplified, because a one-level seam is invisible at true scale and
   * a seam is exactly what one-level differences look like.
   */
  private fun compare(whole: BufferedImage, tiled: BufferedImage, out: File, level: Int) {
    val a = (whole.raster.dataBuffer as DataBufferInt).data
    val b = (tiled.raster.dataBuffer as DataBufferInt).data

    var differing = 0
    var worst = 0
    val diff = BufferedImage(whole.width, whole.height, BufferedImage.TYPE_INT_RGB)
    val d = (diff.raster.dataBuffer as DataBufferInt).data

    for (i in a.indices) {
      val dr = abs(((a[i] ushr 16) and 0xFF) - ((b[i] ushr 16) and 0xFF))
      val dg = abs(((a[i] ushr 8) and 0xFF) - ((b[i] ushr 8) and 0xFF))
      val db = abs((a[i] and 0xFF) - (b[i] and 0xFF))
      val most = maxOf(dr, dg, db)

      if (most > 0) differing++
      if (most > worst) worst = most

      val shown = (most * DIFF_GAIN).coerceAtMost(255)
      d[i] = (shown shl 16) or (shown shl 8) or shown
    }

    val share = 100.0 * differing / a.size
    println(
      "tiled vs whole: %d of %d pixels differ (%.4f%%), worst channel delta %d".format(
        Locale.ROOT, differing, a.size, share, worst
      )
    )

    val file = File(out, "atlas-L%02d-diff.png".format(level))
    if (differing == 0) {
      println("identical - no seams at this level")
      file.delete()
    } else {
      write(out, file.name, diff)
      println("difference amplified ${DIFF_GAIN}x -> ${file.absolutePath}")
    }
  }

  private fun write(dir: File, name: String, image: BufferedImage) {
    val file = File(dir, name)
    ImageIO.write(image, "png", file)
    println("%,d kB -> %s".format(Locale.ROOT, file.length() / 1024, file.absolutePath))
  }

  /**
   * Cap on the single-frame render.
   *
   * One frame allocates the whole pixel buffer plus a sampled raster of the same size, so this is a memory
   * limit rather than a time limit. 32 Mpx is 4096 square with room to spare, which covers every level worth
   * looking at whole - below that the assembly is the only mode that fits, and `-Ptiled` asks for it.
   */
  private const val MAX_PIXELS = 32_000_000L

  /** A one-level seam is invisible at true scale, and one level is what a seam usually is. */
  private const val DIFF_GAIN = 48
}
