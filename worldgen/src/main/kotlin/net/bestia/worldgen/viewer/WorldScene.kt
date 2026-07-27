package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ChunkSeamCheck
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.World
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.VectorFeature
import net.bestia.worldgen.voxel.ChunkMaterializer
import kotlin.math.floor

/**
 * Everything the viewer knows about one world: the fields it can show, the vector features, and -
 * if there is one - the chunk pipeline.
 *
 * Built from a [World] by [of], so a stage added to the pipeline shows up as a new view with no
 * change to the viewer. That is the property that makes this worth building before the geo stages
 * rather than after: every stage from tectonics onward gets an inspector for free.
 */
class WorldScene(
  val name: String,
  val config: WorldConfig,
  val fields: List<ScalarField>,
  val features: FeatureStore,
  val chunkSource: ChunkColumnSource? = null
) {

  /** Vector features grouped by kind, for the status line - "10 rivers, 34 faults" reads better. */
  fun featureSummary(): String = features.all()
    .groupingBy { it.kind }
    .eachCount()
    .entries
    .sortedBy { it.key.ordinal }
    .joinToString(", ") { "${it.value} ${it.key.name.lowercase()}" }
    .ifEmpty { "no vector features" }

  init {
    require(fields.isNotEmpty()) { "A scene needs at least one field to show" }
  }

  val bounds: Aabb get() = config.worldBounds

  fun field(name: String): ScalarField =
    fields.firstOrNull { it.name == name }
      ?: throw IllegalArgumentException("No field '$name'; have ${fields.map { it.name }}")

  /** Features whose influence reaches [area], already in stamp order. */
  fun featuresIn(area: Aabb): List<VectorFeature> = features.query(area)

  /**
   * Runs the chunk-boundary stress check over the chunks currently in view.
   *
   * On demand rather than every frame: it generates the block several times over and would make
   * panning unusable. The point is to be able to ask "is this border real?" while looking at it.
   */
  fun seamCheck(view: Viewport, maxBlock: Int = 8): ChunkSeamCheck.Report? {
    val source = chunkSource ?: return null
    val bounds = view.bounds

    val originX = floor(bounds.minX / config.chunkExtent).toInt()
    val originY = floor(bounds.minY / config.chunkExtent).toInt()
    val across = (bounds.width / config.chunkExtent).toInt() + 2

    return ChunkSeamCheck.run(
      source = source,
      origin = ChunkPos(originX, originY),
      blockSize = across.coerceIn(2, maxBlock)
    )
  }

  companion object {

    /** The whole pipeline, including the materialised block surface. The usual entry point. */
    fun of(generated: GeneratedWorld, name: String = "world"): WorldScene = of(
      world = generated.world,
      name = name,
      base = generated.base,
      chunkSource = generated.columns,
      materializer = generated.materializer
    )

    /**
     * Every raster layer in the world becomes a field, in a stable order, followed by the base
     * heightfield and the chunk views when they are available.
     *
     * Nothing here names a specific stage, which is the property worth keeping: a stage added to the
     * pipeline tomorrow shows up as a new view with no change to the viewer, because [Palettes.forLayer]
     * already has an opinion about how to colour it.
     *
     * @param base the continuous heightfield the chunk tier lifts, if the caller has one
     * @param chunkSource the real chunk pipeline, so the viewer can show generated output rather
     *   than only the inputs to it
     * @param materializer the voxel tier, for the topmost-block view
     */
    fun of(
      world: World,
      name: String = "world",
      base: BaseHeightField? = null,
      chunkSource: ChunkColumnSource? = null,
      materializer: ChunkMaterializer? = null
    ): WorldScene {
      val fields = ArrayList<ScalarField>()

      for (id in world.layers.ids().sortedBy { it.name }) {
        val layer = world.layers[id] ?: continue
        val field = layer.asField(world.config.seaLevel)
        fields.add(field)

        // Both of these span several orders of magnitude and are unreadable on a linear ramp. The log
        // view goes *next to* the raw one rather than replacing it, because the raw values still matter
        // when checking a channel threshold.
        if (id == LayerId.FLOW_ACCUMULATION || id == LayerId.DISCHARGE) {
          fields.add(LogScaledField(field))
        }
      }

      // What erosion did, signed: incision negative, deposition positive. The one view that separates
      // "the tectonic surface" from "the landscape", which are otherwise two nearly identical pictures
      // whose interesting differences are a few hundred metres on a scale of thousands.
      val eroded = fields.firstOrNull { it.name == LayerId.ELEVATION.name }
      val bedrock = fields.firstOrNull { it.name == LayerId.BEDROCK_ELEVATION.name }
      if (eroded != null && bedrock != null) {
        fields.add(
          DifferenceField(
            eroded, bedrock,
            palette = ContinuousPalette(Ramps.DIVERGING, -300.0..300.0),
            name = "erosion",
            unit = "m"
          )
        )
      }

      val elevationPalette = ElevationPalette(world.config.seaLevel)
      base?.let { fields.add(BaseHeightFieldView(it, elevationPalette)) }

      if (chunkSource != null) {
        val chunkField = ChunkHeightField(world.config, chunkSource, elevationPalette)
        fields.add(chunkField)

        // The most useful view of all once features exist: what the vector tier and the detail
        // noise added on top of the raster, signed, so a carve and a fill are different colours.
        val rasterElevation = fields.firstOrNull { it.name == LayerId.ELEVATION.name }
        if (rasterElevation != null) {
          fields.add(DifferenceField(chunkField, rasterElevation, name = "chunk - raster"))
        }
      }

      materializer?.let { fields.add(ChunkSurfaceField(world.config, it)) }

      return WorldScene(name, world.config, fields, world.features, chunkSource)
    }
  }
}
