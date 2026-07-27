package net.bestia.worldgen.viewer

import net.bestia.worldgen.vector.FeatureKind
import java.io.File
import javax.imageio.ImageIO

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
    options: RenderOptions = RenderOptions(autoRange = true)
  ): List<File> {
    directory.mkdirs()

    val renderer = MapRenderer(scene.config)
    val view = Viewport.fit(scene.bounds, widthPx, heightPx)
    val written = ArrayList<File>()

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

    return written
  }

  /**
   * The tightest view of the world that this field can actually be rendered at.
   *
   * Chunk-scale fields refuse a whole-world view, and the voxel field refuses even a kilometre-wide one -
   * materialising two thousand chunks for one picture is a hang, not a render. Rather than dropping those
   * fields from the export, zoom in until each one is affordable. An export that quietly omits the voxel
   * view is worse than a small one: it reads as "the voxel tier produced nothing".
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
}
