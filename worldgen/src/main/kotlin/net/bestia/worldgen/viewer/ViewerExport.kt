package net.bestia.worldgen.viewer

import net.bestia.worldgen.render.Viewport
import net.bestia.worldgen.vector.FeatureKind
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.ceil

/**
 * Renders a scene to PNG files instead of opening a window.
 *
 * Not a lesser version of the interactive viewer - it is the half that works over SSH, in CI, and
 * in a commit. Being able to attach "here is what the erosion stage produces at version 7" to a
 * change is what stops terrain regressions from being argued about from memory.
 */
object ViewerExport {

  /** One PNG per field, plus a chunk-scale close-up when the scene has a chunk pipeline. */
  fun exportAll(
    scene: WorldScene,
    directory: File,
    widthPx: Int = 1400,
    heightPx: Int = 1400,
    // Auto-range by default: an export is looked at without a legend to hover over, and a palette
    // stretched over a range the data does not fill produces a flat picture of a fine field.
    //
    // And the same overlay the window opens with, rather than every kind: an export nobody can click is the
    // one place a legible default matters most, and "all kinds" means four thousand sub-pixel buildings and a
    // second dot painted over every settlement. See RenderOptions.HIDDEN_BY_DEFAULT.
    options: RenderOptions = RenderOptions(
      autoRange = true,
      featureKinds = scene.featureCensus.keys - RenderOptions.HIDDEN_BY_DEFAULT
    )
  ): List<File> {
    directory.mkdirs()

    val renderer = MapRenderer(scene.config, scene::populationOf)
    val view = Viewport.fit(scene.bounds, widthPx, heightPx)
    val written = ArrayList<File>()

    withVoxelScaleBudget(scene, widthPx, heightPx) {
      for (field in scene.fields) {
        val target = viewFor(field, scene, view, widthPx, heightPx)
        val features = if (options.features) scene.featuresIn(target.bounds) else emptyList()
        val map = renderer.render(field, target, options, features)

        if (map.unavailable != null) {
          println("skipped ${field.name}: ${map.unavailable}")
          continue
        }

        written.add(write(map, directory, field.name))
      }
    }

    return written
  }

  /**
   * Runs [body] with every chunk-backed field allowed to generate what a one-pixel-per-voxel picture of
   * this size costs, then puts the budgets back.
   *
   * Restored rather than raised for good: the fields belong to the scene, and a scene that has been
   * exported once must not then hang the interactive viewer because its budgets were left wide open.
   */
  internal fun <T> withVoxelScaleBudget(scene: WorldScene, widthPx: Int, heightPx: Int, body: () -> T): T {
    val budget = voxelScaleBudget(scene, widthPx, heightPx)
    val budgeted = scene.fields.filterIsInstance<ChunkBudgeted>()
    val previous = budgeted.map { it.chunkBudget }

    budgeted.forEach { it.chunkBudget = maxOf(it.chunkBudget, budget) }
    try {
      return body()
    } finally {
      budgeted.forEachIndexed { i, field -> field.chunkBudget = previous[i] }
    }
  }

  /** How many chunks a [widthPx] x [heightPx] image at one pixel per voxel covers. */
  internal fun voxelScaleBudget(scene: WorldScene, widthPx: Int, heightPx: Int): Int {
    val extent = scene.config.chunkExtent
    val across = widthPx * scene.config.voxelSize / extent + 1.0
    val down = heightPx * scene.config.voxelSize / extent + 1.0

    return ceil(across * down).toInt().coerceAtMost(MAX_EXPORT_CHUNKS)
  }

  /**
   * The tightest view of the world that this field can actually be rendered at.
   *
   * Chunk-scale fields refuse a whole-world view, so rather than dropping them from the export, fall back
   * to the voxel-scale close-up. An export that quietly omits the voxel view is worse than a small one: it
   * reads as "the voxel tier produced nothing".
   *
   * The halving loop only bites when the image is large enough that even the raised budget cannot cover it,
   * and it says so - a sub-voxel picture of a quarter of the area looks exactly like a 1:1 one and would
   * otherwise silently misrepresent the scale everything in it was measured at.
   */
  private fun viewFor(
    field: ScalarField,
    scene: WorldScene,
    world: Viewport,
    widthPx: Int,
    heightPx: Int
  ): Viewport {
    if (field.availabilityFor(world) == null) return world

    var candidate = closeUp(scene, widthPx, heightPx)
    var attempts = 0
    while (field.availabilityFor(candidate) != null && attempts++ < MAX_ZOOM_STEPS) {
      candidate = candidate.zoomedAtCenter(2.0)
    }

    if (candidate.metresPerPixel < scene.config.voxelSize) {
      println(
        "${field.name}: ${"%.3f".format(Locale.ROOT, candidate.metresPerPixel)} m/px, finer than one pixel per voxel - " +
            "${widthPx}x$heightPx at voxel scale exceeds the $MAX_EXPORT_CHUNKS chunk export budget"
      )
    }

    return candidate
  }

  fun write(map: RenderedMap, directory: File, name: String): File {
    val file = File(directory, "${name.replace(NON_FILENAME, "-")}.png")
    ImageIO.write(map.image, "png", file)
    return file
  }

  /**
   * A voxel-scale window for the fields that only exist at chunk resolution.
   *
   * Centred on a point *on* a feature rather than on the world, because the middle of the map is
   * usually open sea and an export that shows nothing is worse than no export - it looks like the
   * chunk tier produced nothing.
   */
  private fun closeUp(scene: WorldScene, widthPx: Int, heightPx: Int): Viewport {
    val features = scene.featuresIn(scene.bounds)
    val subject = features.firstOrNull { it.kind == FeatureKind.RIVER_CHANNEL } ?: features.firstOrNull()
    val centre = subject?.outline()?.firstOrNull()?.let { it.pointAt(it.length / 2.0) }

    val bounds = scene.bounds
    return Viewport(
      centerX = centre?.x ?: ((bounds.minX + bounds.maxX) / 2.0),
      centerY = centre?.y ?: ((bounds.minY + bounds.maxY) / 2.0),
      metresPerPixel = scene.config.voxelSize,
      widthPx = widthPx,
      heightPx = heightPx
    )
  }

  private val NON_FILENAME = Regex("[^A-Za-z0-9_.-]+")

  /** Enough halvings to take a kilometre-wide view down to a few tens of metres. */
  private const val MAX_ZOOM_STEPS = 8

  /**
   * The ceiling on a voxel-scale export, in chunks - about a 4 km square at one metre per voxel.
   *
   * An offline render has no frame deadline, but it does have a person waiting for it, and every chunk here
   * is a thousand columns materialised. Past this the export falls back to a sub-voxel view of a smaller
   * area, which is a slow picture rather than one nobody waits for.
   */
  private const val MAX_EXPORT_CHUNKS = 16_384
}
