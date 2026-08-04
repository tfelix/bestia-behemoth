package net.bestia.worldgen.spawn

import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.bio.VegetationStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.mana.CorruptionStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.SurfaceCover
import net.bestia.worldgen.voxel.VegetationParams
import kotlin.math.PI

/** Station channels on a [FeatureKind.VEGETATION_STAND] marker. */
object VegetationStandChannels {

  /** Metres from the marker this stand is responsible for. Maps onto a runtime's activation radius. */
  const val RADIUS = "radius"

  /** `LayerId.CANOPY_COVER` at the marker, 0..1. How wooded the stand is. */
  const val COVER = "cover"

  /** [Biome] ordinal here. Read with `Biome.entries[v.toInt()]`; never interpolated. */
  const val BIOME = "biome"

  const val CORRUPTION = "corruption"

  /**
   * How many tree props a runtime should expect to find inside [RADIUS].
   *
   * The load-bearing channel, and the direct analogue of `DepositChannels.TONS`: a number the world tier
   * advertises which the chunk tier has to be able to fill. Derived from the **same** [VegetationParams] the
   * prop emitter uses - see [VegetationParams.entitiesPerSquareMetre] - which is what makes
   * `checkVegetationStandsAdvertiseFillableCapacity` able to compare the two rather than merely assert that
   * both are plausible.
   *
   * Not `COVER` times the area. Cover is a void fraction, so a tree count cannot be read off it directly;
   * the bridge between them is `coverOf`'s inverse and it lives in one place.
   */
  const val CAPACITY = "capacity"
}

/** Tuning for [VegetationStandStage]. */
data class VegetationStandParams(

  /**
   * Metres between stand candidates.
   *
   * Used raw rather than detail-scaled, for the reason `SpawnerParams.candidateSpacing` gives: how many
   * patches of wood a runtime is asked to look after per square kilometre is a gameplay density, and a small
   * world wants the same one a large world has.
   *
   * Quadratic in the marker count, so this is the wrong lever for anything but the marker count itself.
   *
   * ### Coupled to [standRadius], and that is what sets it
   *
   * A jittered grid at this spacing leaves the worst-covered point at `spacing * sqrt(2) / 2` from the
   * nearest centre, so full coverage of the wooded ground needs `spacing <= standRadius * sqrt(2)` - 566 m at
   * a 400 m radius. Six hundred is a shade over that and so leaves small gaps, which is the right side to err
   * on: a gap costs one patch of wood no director, and closing it by widening the radius costs granularity
   * everywhere.
   *
   * **Measured on Genesis: 9 994 stands, each advertising about 2 300 trees**, and a sampled stand holds 17 to
   * 50 tree props per hectare. That is three times the estimate this was designed against and it is not bloat -
   * the discs cover about thirty per cent of the land after overlap, which is roughly the share of it that is
   * wooded at [minCover]. Reducing the count means coarser granularity or woods with nothing watching them,
   * not less waste.
   *
   * The stage itself costs 2 to 6 ms of a 612 ms world. The `FeatureStore` roughly doubling is the real price,
   * and the thing to watch is `ChunkMaterializer.materialize`'s per-chunk feature query, which every other
   * chunk-tier producer iterates past. `FeatureIndex` metrics stayed healthy - one oversized entry, mean
   * bucket 7.6 - so it has not bitten yet.
   *
   * The count fell from 11 139 as the shoreline and snow-cap vetoes below were added, which is the useful way
   * to read those two: a tenth of the stands this stage first placed were looking after nothing.
   */
  val standSpacing: Double = 600.0,

  /**
   * Metres a stand is responsible for.
   *
   * Deliberately **larger than [standSpacing]**, so stands overlap and a wood has no seams between the
   * patches watching it. That is also why the placement does not need Poisson-disc quality - see the class
   * KDoc.
   *
   * Exceeds `ChunkMaterializer.MARKER_MARGIN`, which is fine and worth saying because a reviewer will ask: a
   * stand has `affectsHeight = false` and materialises no voxel, so no chunk has to find it by that margin
   * and it does not join `checkStructuralMarkersFitTheQueryMargin`'s list. A runtime queries stands by this
   * radius, not by the chunk margin.
   */
  val standRadius: Double = 400.0,

  /**
   * Canopy cover below which no stand is placed at all.
   *
   * A cutoff rather than a taper, so an open plain with a few stray trees on it is not littered with
   * directors. `VegetationParams.clearingCutoff` plays the same role one tier down.
   *
   * **Set at about the mean canopy over land**, which the reference worlds measure at 0.126 to 0.145, so a
   * stand sits on above-average ground by construction. That is a deliberately higher bar than "has anything
   * to do": at a cover of 0.05 a 400 m disc still holds some 340 trees, which is plenty of work, but at one
   * tree every 38 m it is savanna rather than a wood - and `checkVegetationStandsAreWooded` caught exactly
   * that, reporting stands averaging 1.48 times the land mean where a wood should be well clear of it.
   *
   * The cost is that sparse country has no director. Accepted: the trees there still exist - a prop does not
   * depend on a stand - and what a stand owns is the state of a *wood*, which is what a scatter of trees over
   * a plain is not.
   */
  val minCover: Double = 0.12
) : Params {

  init {
    require(standSpacing > 0.0) { "standSpacing must be positive, was $standSpacing" }
    require(standRadius > 0.0) { "standRadius must be positive, was $standRadius" }
    require(minCover in 0.0..1.0) { "minCover must be in [0,1], was $minCover" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    standSpacing = source.double("standSpacing", standSpacing),
    standRadius = source.double("standRadius", standRadius),
    minCover = source.double("minCover", minCover)
  )

  override fun digest() = ParamsDigest()
    .put("standSpacing", standSpacing)
    .put("standRadius", standRadius)
    .put("minCover", minCover)
}

/**
 * Stands of trees: the patches of wood a runtime is responsible for.
 *
 * A tree is a function of position and there are millions of them per world, so no runtime can hold them all
 * and none of them can own anything. A **stand** can: it is one marker per patch of wood, few enough to be
 * resident for the whole world at once, and it is where the state a wood needs but a tree cannot have lives -
 * how healthy the canopy is, whether it is on fire, what is regrowing and when.
 *
 * The same kind of object as a [FeatureKind.BESTIA_SPAWN] den one rung up in scale, and modelled on
 * `SpawnerStage` accordingly. In `spawn/` rather than in `bio/` for that reason and for a harder one: this
 * reads the corruption, and `bio/` deliberately does not - `SurfaceSampler`'s note records that
 * `VegetationStage` runs *before* `CorruptionStage` and must not see it, and keeping the two stages in
 * different packages keeps that separation visible rather than depending on nobody adding an import.
 *
 * ### A jittered grid, not a Poisson disc
 *
 * `SpawnerStage` samples with [net.bestia.worldgen.fields.PoissonDisk] at 2500 m, which is affordable. A
 * stand wants roughly 600 m, which is seventeen times finer and 290 times the candidates - on a 512 km world
 * that is a single-threaded Bridson pass over half a million points with a 1200-squared background grid.
 *
 * And the quality it buys is not wanted here. A Poisson disc guarantees a minimum separation, which matters
 * when a den's pack should not overlap its neighbour's; a stand's [VegetationStandParams.standRadius] already
 * *exceeds* its spacing by design, so stands are meant to overlap. One jittered candidate per grid cell is
 * O(cells), parallel by construction, and deterministic from the cell index rather than from a sequential
 * stream.
 *
 * ### What it reads, and the four layers it deliberately does not
 *
 * `CANOPY_COVER`, `BIOME`, `CORRUPTION`, and the two water tests. Not `SOIL_DEPTH`, `SOIL_FERTILITY`,
 * `PRECIPITATION` or `TEMPERATURE`: every one of those is already inside `CANOPY_COVER` -
 * `VegetationScatter.densityAt` folds the biome and the soil in, and the classifier chose the biome *from*
 * the rainfall. Reading them again would square the same evidence and call it two, which is the error that
 * file's KDoc is written around.
 */
class VegetationStandStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: VegetationStandParams = VegetationStandParams(),

  /**
   * The vegetation tuning, forwarded rather than defaulted.
   *
   * A default would compile and would be wrong the moment a params file moved `entityShare`: every stand in
   * the world would advertise a capacity computed from a different lattice than the one the chunk tier
   * plants, and both numbers would still look plausible. The same argument `ChunkMaterializer.caveParams`
   * makes.
   */
  private val vegetation: VegetationParams = VegetationParams()
) : Stage {

  override val id = ID
  override val version = 1

  override val paramsVersion get() = GenRng.hash(params.digest().value, vegetation.digest().value)

  override val dependencies = listOf(
    ClimateStage.ID,
    GlacialStage.ID,
    HydrologyStage.ID,
    BiomeStage.ID,
    VegetationStage.ID,
    CorruptionStage.ID
  )

  override val scale = StageScale.WORLD

  override val outputs = listOf(StageOutput.Vector(FeatureKind.VEGETATION_STAND))

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
    val waterLevel = Grid.from(ctx.layers.float(LayerId.WATER_LEVEL))
    val canopy = Grid.from(ctx.layers.float(LayerId.CANOPY_COVER))
    val corruption = Grid.from(ctx.layers.float(LayerId.CORRUPTION))
    val biome = ctx.layers.int(LayerId.BIOME)
    val temperature = ctx.layers.float(LayerId.TEMPERATURE)
    val seaLevel = ctx.config.seaLevel

    val world = region.toWorld()
    val nextId = FeatureIds.allocator(id)
    val stands = ArrayList<VectorFeature>()

    val gridFromX = Math.floorDiv(world.minX.toLong(), params.standSpacing.toLong())
    val gridUntilX = Math.floorDiv(world.maxX.toLong(), params.standSpacing.toLong()) + 1
    val gridFromY = Math.floorDiv(world.minY.toLong(), params.standSpacing.toLong())
    val gridUntilY = Math.floorDiv(world.maxY.toLong(), params.standSpacing.toLong()) + 1

    val area = PI * params.standRadius * params.standRadius

    for (gridY in gridFromY until gridUntilY) {
      for (gridX in gridFromX until gridUntilX) {
        val hash = GenRng.hash(ctx.seed, JITTER_SALT, gridX, gridY)
        val at = Vec2d(
          gridX * params.standSpacing + GenRng.unit(hash) * params.standSpacing,
          gridY * params.standSpacing + GenRng.unit(GenRng.mix64(hash)) * params.standSpacing
        )

        val cellX = (at.x / metres).toInt() - region.minX
        val cellY = (at.y / metres).toInt() - region.minY
        if (cellX !in 0 until region.width || cellY !in 0 until region.height) continue
        val cell = cellY * region.width + cellX

        // Nothing stands on water, and nothing stands on the shore either. Both water tests, because they
        // answer different questions - the elevation one is the sea and the water level one is every lake and
        // pond the priority flood found - and both applied to the **four neighbours** as well as to the cell.
        //
        // The neighbourhood is not caution, it is a correction. These are kilometre rasters and the ground a
        // tree actually stands on is the detailed column, which swings tens of metres either side of the cell
        // average: the first stand this stage ever placed sat in a cell the raster called land at a position
        // whose real ground was 13 m under the sea, advertising a capacity the water veto guarantees no chunk
        // will ever fill. A cell whose neighbours are all dry cannot be that cell.
        //
        // Costs a stand within a kilometre of any shoreline. Accepted: the trees there still exist, and what a
        // stand owns is the state of a wood rather than the trees themselves.
        if (isCoastal(elevation, waterLevel, region, cellX, cellY, seaLevel)) continue

        val here = Biome.entries[biome[region.minX + cellX, region.minY + cellY]]
        if (here.isWater) continue

        val cover = canopy.data[cell].toDouble()
        if (cover < params.minCover) continue

        // The same veto the prop emitter applies, and it has to be here or the advertisement is a lie.
        // `CANOPY_COVER` is biome times soil times patch and knows nothing about what the top of a column is
        // made of, so it reads a snowbound conifer cell as wooded - while every tree there is refused by the
        // cap test. The second stand this stage ever placed sat on TEMPERATE_FOREST at -6.4 C advertising
        // 2 300 trees that no chunk would ever produce.
        //
        // Read through world coordinates rather than by index: the climate runs coarser than the heightfield,
        // and indexing a coarser layer with a kilometre-grid index clamps rather than fails.
        val cap = SurfaceCover.cap(here, temperature.sampleBilinear(at.x, at.y), 0.0, false)
        if (cap == BlockType.ICE || cap == BlockType.SNOW) continue

        val capacity = vegetation.entitiesPerSquareMetre(cover) * area

        // A stand that would look after nothing is not a stand. Reachable just above `minCover`, where the
        // cover is real but the entity share thins it to less than one tree in the whole disc.
        if (capacity < 1.0) continue

        stands.add(
          PointMarker(
            nextId(),
            FeatureKind.VEGETATION_STAND,
            at,
            StationTable.Builder(stationCount = 1)
              .channel(VegetationStandChannels.RADIUS) { params.standRadius }
              .channel(VegetationStandChannels.COVER) { cover }
              .channel(VegetationStandChannels.BIOME) { here.ordinal.toDouble() }
              .channel(VegetationStandChannels.CORRUPTION) { corruption.data[cell].toDouble() }
              .channel(VegetationStandChannels.CAPACITY) { capacity }
              .build()
          )
        )
      }
    }

    return StageResult(features = stands)
  }

  /**
   * Whether this cell or any of its four neighbours is sea or standing water.
   *
   * Four rather than eight: a diagonal touch is a corner, and excluding it would push the exclusion band out
   * to a diagonal kilometre for no gain. Cells outside the region count as water, which is the safe answer at
   * the world edge - the ocean margin is out there.
   */
  private fun isCoastal(
    elevation: Grid,
    waterLevel: Grid,
    region: CellRegion,
    cellX: Int,
    cellY: Int,
    seaLevel: Double
  ): Boolean {
    for ((dx, dy) in NEIGHBOURS) {
      val x = cellX + dx
      val y = cellY + dy
      if (x !in 0 until region.width || y !in 0 until region.height) return true

      val at = y * region.width + x
      if (elevation.data[at] <= seaLevel) return true
      if (!waterLevel.data[at].isNaN()) return true
    }

    return false
  }

  companion object {
    private val NEIGHBOURS = arrayOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)

    /**
     * Named for what it produces rather than for the file it lives in.
     *
     * The topological sort breaks ties on the stage *name*, so the position this lands in is a consequence of
     * the dependencies above and of this string - which is why the dependency list is exhaustive rather than
     * minimal. See the architecture document's note on the glacial stage, which executed in the right order
     * for years by alphabetical accident.
     */
    val ID = StageId("vegetation_stands")

    private const val JITTER_SALT = 0x5A4E44E7L
  }
}
