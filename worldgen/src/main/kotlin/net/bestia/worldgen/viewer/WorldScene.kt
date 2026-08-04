package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ChunkSeamCheck
import net.bestia.worldgen.core.FeatureStore
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.NavGraph
import net.bestia.worldgen.core.World
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
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
  val chunkSource: ChunkColumnSource? = null,
  /** The macro navigation graph, or [NavGraph.EMPTY] for a pipeline that had no navigation stage. */
  val navGraph: NavGraph = NavGraph.EMPTY
) {

  /**
   * The prepared navigation overlay: a spatial index over the graph plus the drawing itself.
   *
   * Held here rather than built by the renderer because the renderer is rebuilt whenever the shown field
   * changes, and this index is O(total waypoints) to construct - on a large world that is hundreds of
   * thousands of points, which is fine once and not fine on every click in the field list. Lazy, so a world
   * nobody asks the overlay about never pays for it at all.
   */
  val navOverlay: NavGraphOverlay by lazy { NavGraphOverlay(navGraph) }

  /**
   * How many features of each kind this world has, in [FeatureKind] declaration order.
   *
   * Computed once and held, for two reasons. [FeatureStore.all] copies the whole list under a lock, which is
   * fine at startup and not fine per repaint; and the store is frozen after the vector stages, so a census
   * taken here can never go stale.
   *
   * The legend reads this rather than the whole store, and it reads it to decide *which rows exist* - only the
   * kinds a world actually has, so a world with no glaciers has no glacier row to wonder about.
   */
  val featureCensus: Map<FeatureKind, Int> = features.all()
    .groupingBy { it.kind }
    .eachCount()
    .entries
    .sortedBy { it.key.ordinal }
    .associateTo(LinkedHashMap()) { it.key to it.value }

  /**
   * Present-day population per settlement index, where history has recorded one.
   *
   * Keyed on [SettlementChannels.INDEX], which is the join key everything downstream of placement uses. The
   * value comes from the `SETTLEMENT_HISTORY` marker rather than from the settlement's own marker, and the
   * difference matters for a map: the settlement carries the population the *site* could support when it was
   * placed, and this carries what a thousand years of history actually left there. A place that was sacked
   * three times should not draw as big as it was zoned for.
   *
   * Empty when the pipeline has no history stage, in which case the marker's own figure is all there is.
   */
  private val livePopulations: Map<Int, Double> = features.all()
    .asSequence()
    .filterIsInstance<PointMarker>()
    .filter { it.kind == FeatureKind.SETTLEMENT_HISTORY }
    .mapNotNull { marker ->
      val index = marker.optionalAttribute(HistoryChannels.INDEX) ?: return@mapNotNull null
      val population = marker.optionalAttribute(HistoryChannels.POPULATION) ?: return@mapNotNull null
      index.toInt() to population
    }
    .toMap()

  /** Vector features grouped by kind, for the status line - "10 rivers, 34 faults" reads better. */
  fun featureSummary(): String = featureCensus.entries
    .joinToString(", ") { "${it.value} ${it.key.name.lowercase()}" }
    .ifEmpty { "no vector features" }

  /**
   * How many people live at a settlement marker today, or null when it does not say.
   *
   * Prefers what history left over what placement intended; see [livePopulations].
   */
  fun populationOf(settlement: PointMarker): Double? {
    val index = settlement.optionalAttribute(SettlementChannels.INDEX)?.toInt()
    return index?.let { livePopulations[it] } ?: settlement.optionalAttribute(SettlementChannels.POPULATION)
  }

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

      // Summer against winter, signed. Four seasonal maps side by side answer "how much rain in this quarter"
      // and none of them answers "where is the monsoon", which is a question about the *difference* - and the
      // one that says whether the seasonal fields are doing anything at all. A world where this view is flat
      // grey has four copies of one field, which is the failure mode of storing four seasons; it is also where
      // the hemispheres show up, because the sign has to flip across the equator.
      val summer = fields.firstOrNull { it.name == LayerId.PRECIPITATION_SUMMER.name }
      val winter = fields.firstOrNull { it.name == LayerId.PRECIPITATION_WINTER.name }
      if (summer != null && winter != null) {
        fields.add(
          DifferenceField(
            summer, winter,
            palette = ContinuousPalette(Ramps.DIVERGING, -600.0..600.0),
            name = "monsoon (summer - winter)",
            unit = "mm"
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

      materializer?.let {
        fields.add(ChunkSurfaceField(world.config, it))
        fields.add(SurfaceOccupancyField(world.config, it))
      }

      // First in the list, so the viewer opens on a map of the world rather than on whichever raster happens
      // to sort first alphabetically - which is `bedrock_elevation`, a picture of the land before erosion, and
      // about the least useful thing to be shown by a tool you have just opened to look at a world.
      worldMapOf(world)?.let { fields.add(0, it) }

      return WorldScene(name, world.config, fields, world.features, chunkSource, world.navGraph)
    }

    /**
     * The composed map, or null when this world has no biomes to colour it with.
     *
     * Nullable rather than required because a scene is also built from partial pipelines - the stage tests
     * each run a handful of stages - and a missing view is better than a viewer that cannot open on a world
     * that stops before `BiomeStage`.
     */
    private fun worldMapOf(world: World): WorldMapField? {
      val elevation = world.layers[LayerId.ELEVATION] as? FloatLayer ?: return null
      val biome = world.layers[LayerId.BIOME] as? IntLayer ?: return null

      return WorldMapField(
        elevation = elevation,
        biome = biome,
        water = world.layers[LayerId.WATER_LEVEL] as? FloatLayer,
        ice = world.layers[LayerId.ICE_THICKNESS] as? FloatLayer,
        seaLevel = world.config.seaLevel
      )
    }
  }
}
