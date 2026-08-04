package net.bestia.worldgen.geo

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Parallel
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.IntGrid
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.fields.PoissonDisk
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tuning for [TectonicsStage].
 *
 * A params object rather than constants in the stage, and rather than fields on `WorldConfig`. These are the
 * numbers a designer changes to make a world archipelagic or continental, so they are **loadable from a params
 * file** - `./gradlew :worldgen:invariants -Pparams=my.params` - and validated on construction by the `init`
 * block below. See [overriddenBy] for the loader and `core/ParamsText.kt` for the format.
 *
 * They are deliberately not `WorldConfig` fields. A field there has to join `shapeVersion`'s explicit list and
 * then `PersistedWorld`, `WorldConfigMapping`, `WorldGenSettings.FLAGS` and `WorldArgs` all need it - four files
 * and a database column each. Params ride `pipelineVersion` instead, which is already a persisted column, so
 * the whole set costs nothing new.
 */
data class TectonicsParams(

  /**
   * Target plate spacing in metres. Null derives it from the world size, which is what any world
   * smaller than a real planet needs - 700 km plates in a 500 km world give you one plate.
   */
  val plateSpacing: Double? = null,

  /**
   * Fraction of plates that are oceanic. Earth is about 0.6 by count.
   *
   * **The real lever on how much of the world is land**, despite the name of the one below it. Continental and
   * oceanic crust sit in two well-separated elevation clusters - about +300 m and about -3400 m - so this
   * decides roughly what share of the world is *capable* of being land, and [targetLandFraction] only decides
   * where in the gap between the two the waterline falls.
   *
   * Ask for more land than the continental share can supply and the waterline is forced down into the oceanic
   * cluster, where the land that surfaces is ex-seafloor: no uplift behind it, no orogenic relief, and a
   * coastline that follows the plate diagram because the per-plate base elevation is the only thing varying
   * there. Keep the two roughly in step and the shoreline instead lands on the boundary cross-fade, which is
   * a domain-warped continental shelf - which is what a coast should be.
   */
  val oceanicShare: Double = 0.45,

  /**
   * Fraction of **the whole world**, forced ocean margin included, that the bedrock leaves above sea level.
   *
   * Enforced by shifting the whole heightfield until it is true. Without it the land fraction swings from 5% to
   * 80% between seeds depending on how the Poisson sampler happened to place the continental plates, and most
   * seeds are unusable.
   *
   * "The whole world" is load bearing. Measured over the interior only - which is what a quantile over the
   * unmargined cells gives you - the number meant something different on every world size, because the margin
   * is a sixth of a 128 km world's short edge and a fiftieth of a 512 km one's. See [normaliseLandFraction].
   *
   * Note this is the lever for *how much* land, not for *what kind*. Raising it alone drops the waterline into
   * the oceanic plates' own elevation cluster, and what surfaces there is ex-seafloor: no uplift, so erosion
   * planes it flat instead of carving it, and a coastline that follows a Voronoi contour. [oceanicShare] is the
   * knob that decides whether the new land is continental crust.
   */
  val targetLandFraction: Double = 0.50,

  /**
   * How far below sea level the forced ocean margin is taken, in metres.
   *
   * Deep enough to be unmistakably open sea rather than shallows a player might mistake for a wadeable shelf,
   * and deep enough that erosion and hydrology treat it as the sink it is. See [OceanBorder].
   */
  val oceanBorderDepth: Double = 400.0,

  /**
   * How far inland the ocean margin's outer edge is allowed to wander, in metres.
   *
   * The margin's *guarantee* is a rectangle - it has to be, because that is the shape of the world - but its
   * shoreline need not be, and it was: `OceanBorder.distanceToEdge` measured distance to a rectangle, so the
   * coast came out as four straight lines parallel to the map's edges meeting at square corners. On a small
   * world, where the margin is a sixth of the short edge, that was most of the visible coastline.
   *
   * This buys a coastline back. It only ever pushes the drowning *further in*, never out, so the guarantee is
   * untouched - see [OceanBorder.distanceToEdge] for why that is the necessary and sufficient condition.
   *
   * It costs land: the mean margin widens by half of this on every side, which on a 128 km world is a couple
   * of points off the land fraction. That is accounted for rather than absorbed - the land-fraction search
   * measures the finished heights, margin included.
   */
  val oceanBorderWobble: Double = 2_500.0,

  /** Wavelength of the ridged relief noise in metres - the spacing of individual ridge crests. */
  val reliefWavelength: Double = 38_000.0,

  /** Peak crest height in an inactive plate interior, in metres. */
  val interiorRelief: Double = 240.0,

  /**
   * Amplitude of the plate-independent regional swell, in metres.
   *
   * The knob that decides how much the coastline owes to the plate layout. At zero the shoreline is a
   * contour of the Voronoi diagram; turned up it wanders across plate boundaries and the tectonic
   * structure shows in the mountains rather than in the outline of the land.
   *
   * It matters more on a land-dominated world than on an ocean-dominated one, because it is what puts bays,
   * gulfs and peninsulas into a coastline that would otherwise run straight round the outside of a continent.
   * Do not take it much past 900: at that point it is inventing continents of its own and the plate structure
   * stops reading as the reason for anything.
   */
  val continentalSwell: Double = 650.0,

  /**
   * Additional crest height at the heart of an orogen, in metres.
   *
   * Lowered from 1750 when [oceanicShare] came down. The two multiply: a more continental world has more
   * continent-continent convergence, and each of those is worth another 3400 m of collision uplift on top of
   * this, so keeping both at their old values took a 128 km world to 6.5 km peaks. Which is not merely ugly -
   * it puts most of the land above the treeline and much of it above the snowline, throws enormous ice flux
   * into the glacial stage, and drops a rain shadow behind every ridge, so the interiors go to desert and the
   * rivers with them.
   *
   * Lowered again to 820 for the flat land. This is the term that decides how much of a continent is *crest*
   * rather than country: the ridged-noise crest field it scales is applied over the whole orogen, so raising
   * it does not make one range taller so much as it makes a wider band of the map steep. At 1150, ground steep
   * enough to be bare was 13-14% of all land on its own, before counting the alpine and cold ground behind it.
   * (Measured when that ground carried a `CLIFF` biome, which made it one number to read off the biome mix.
   * The biome is gone - see `Biome` - and the terrain it described is not, so the measurement still holds.)
   */
  val orogenicRelief: Double = 820.0,

  /** Hotspot chain spacing in metres. Null derives it from the plate spacing. */
  val hotspotSpacing: Double? = null,

  /** Islands per hotspot chain. */
  val hotspotChainLength: Int = 7
) : Params {
  init {
    // Null means "derived from the world's size", which is a value the file format spells `default` - so the
    // constraint is on the number when there is one, not on its presence.
    plateSpacing?.let { require(it > 0.0) { "plateSpacing must be positive when set, was $it" } }
    require(oceanicShare in 0.0..1.0) { "oceanicShare must be in [0,1], was $oceanicShare" }
    require(targetLandFraction in 0.01..0.99) {
      "targetLandFraction must be in (0,1), was $targetLandFraction"
    }
    require(oceanBorderDepth >= 0.0) { "oceanBorderDepth must not be negative, was $oceanBorderDepth" }
    require(oceanBorderWobble >= 0.0) { "oceanBorderWobble must not be negative, was $oceanBorderWobble" }
    require(reliefWavelength > 0.0) { "reliefWavelength must be positive, was $reliefWavelength" }
    require(interiorRelief >= 0.0) { "interiorRelief must not be negative, was $interiorRelief" }
    require(continentalSwell >= 0.0) { "continentalSwell must not be negative, was $continentalSwell" }
    require(orogenicRelief >= 0.0) { "orogenicRelief must not be negative, was $orogenicRelief" }
    hotspotSpacing?.let { require(it > 0.0) { "hotspotSpacing must be positive when set, was $it" } }
    require(hotspotChainLength >= 1) { "hotspotChainLength must be at least 1" }
  }

  /**
   * This object with whatever a params file overrides, in one `copy` so `init` sees a consistent set.
   *
   * One call rather than a field at a time, because `copy` re-runs `init` and the cross-field constraints would
   * fail on an intermediate state - `targetLandFraction` alone is fine, but a class whose `min` is applied
   * before its `max` would reject the very file that sets both correctly.
   */
  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    plateSpacing = source.doubleOrDerived("plateSpacing", plateSpacing),
    oceanicShare = source.double("oceanicShare", oceanicShare),
    targetLandFraction = source.double("targetLandFraction", targetLandFraction),
    oceanBorderDepth = source.double("oceanBorderDepth", oceanBorderDepth),
    oceanBorderWobble = source.double("oceanBorderWobble", oceanBorderWobble),
    reliefWavelength = source.double("reliefWavelength", reliefWavelength),
    interiorRelief = source.double("interiorRelief", interiorRelief),
    continentalSwell = source.double("continentalSwell", continentalSwell),
    orogenicRelief = source.double("orogenicRelief", orogenicRelief),
    hotspotSpacing = source.doubleOrDerived("hotspotSpacing", hotspotSpacing),
    hotspotChainLength = source.int("hotspotChainLength", hotspotChainLength)
  )

  override fun digest() = ParamsDigest()
    .putOrDerived("plateSpacing", plateSpacing)
    .put("oceanicShare", oceanicShare)
    .put("targetLandFraction", targetLandFraction)
    .put("oceanBorderDepth", oceanBorderDepth)
    .put("oceanBorderWobble", oceanBorderWobble)
    .put("reliefWavelength", reliefWavelength)
    .put("interiorRelief", interiorRelief)
    .put("continentalSwell", continentalSwell)
    .put("orogenicRelief", orogenicRelief)
    .putOrDerived("hotspotSpacing", hotspotSpacing)
    .put("hotspotChainLength", hotspotChainLength)
}

/**
 * Stage 1: plate tectonics and the base heightfield.
 *
 * Starts from plates rather than from fractal noise, which is the difference between terrain that has
 * reasons and terrain that is the same everywhere. A range exists because two continental plates are
 * closing; a trench exists because one plate is going under another; a chain of islands exists because
 * a plate drifted over a hotspot. Every one of those is legible to a player who never reads a word of
 * documentation, and none of them survive being replaced by an octave of noise.
 *
 * Emits the plate boundaries as [FeatureKind.FAULT] markers so that the six stages which want them -
 * ore genesis, volcanism, resource placement - read one authoritative geometry instead of each
 * re-deriving it from `plate_id`.
 */
class TectonicsStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: TectonicsParams = TectonicsParams()
) : Stage {

  override val id = ID
  override val version = 1

  override val paramsVersion get() = params.digest().value
  override val dependencies: List<StageId> = emptyList()
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.BEDROCK_ELEVATION),
    StageOutput.Raster(LayerId.PLATE_ID),
    StageOutput.Raster(LayerId.ROCK_HARDNESS),
    StageOutput.Raster(LayerId.CRUST_AGE),
    StageOutput.Raster(LayerId.UPLIFT),
    StageOutput.Vector(FeatureKind.FAULT),
    StageOutput.Vector(FeatureKind.HOTSPOT)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val bounds = region.toWorld()
    val spacing = params.plateSpacing ?: defaultSpacing(ctx.config, bounds.width, bounds.height)

    /**
     * How much of a full-size orogeny this world builds at each boundary.
     *
     * A deliberate, and deliberately unphysical, trade. A continent-continent collision raises 3400 m because
     * that is what one raises on Earth, and it is right for a world with Earth's ratio of plate boundary to
     * area. A world at detail scale four has four times that ratio - that is what detail scale *is*, a small
     * world given a big world's feature density - so leaving the amplitude alone gives every one of those
     * boundaries a Himalaya, and the result is not a dramatic world but a uniformly vertical one: measured,
     * 22% of the land too steep to carry soil and 6% forest and grassland together. (Measured against the
     * `CLIFF` biome, since removed; the terrain it counted is unchanged.)
     *
     * The square root splits the difference rather than cancelling the effect: a 128 km world gets ranges at
     * about half height, which is still the tallest thing in it by a wide margin, and gets lowland between them
     * to put fields and forests on. Exactly 1.0 at 512 km and above, so the reference world is untouched.
     */
    val orogenicHeight = 1.0 / sqrt(ctx.config.detailScale)

    val plates = PlateSet.build(bounds, spacing, ctx.rng(PLATE_STREAM), params.oceanicShare)

    val elevation = Grid(region.width, region.height)
    val plateId = IntGrid(region.width, region.height)
    val uplift = Grid(region.width, region.height)
    val crustAge = Grid(region.width, region.height)

    /** How caught up in mountain building each cell is; drives relief roughness and rock hardness. */
    val intensity = Grid(region.width, region.height)

    /**
     * Continuous "how oceanic is this crust", 0 to 1.
     *
     * Rock hardness needs it, and taking the nearest plate's discrete type instead would put a step in
     * the hardness field along every plate boundary - which erosion would then turn into a straight
     * escarpment, reintroducing the polygon edges through the back door.
     */
    val oceanicity = Grid(region.width, region.height)

    val reliefSeed = GenRng.mix64(ctx.seed xor RELIEF_SALT)
    val undulationSeed = GenRng.mix64(ctx.seed xor UNDULATION_SALT)
    val continentSeed = GenRng.mix64(ctx.seed xor CONTINENT_SALT)

    Timings.measure("tectonics.mainLoop") {
    // Twenty-odd noise octaves and a nearest-two plate query per cell, every one of them a pure function
    // of world position - the most straightforwardly separable block in the pipeline. The two scratch
    // arrays exist to keep `sampleInto` allocation-free and so become per band; `PlateSet.contact` used to
    // be the other obstacle and is now a lock-free table.
    Parallel.rows(region.height, region.width) { yFrom, yUntil ->
    val sample = DoubleArray(3)
    val scratch = DoubleArray(4)

    for (y in yFrom until yUntil) {
      val worldY = (region.minY + y + 0.5) * metres
      for (x in 0 until region.width) {
        val worldX = (region.minX + x + 0.5) * metres
        val i = elevation.index(x, y)

        plates.sampleInto(worldX, worldY, sample, scratch)
        val own = plates.plates[sample[0].toInt()]
        val otherId = sample[1].toInt()
        val distance = sample[2]

        plateId.data[i] = own.id

        // Cross-fade weight: 0 exactly on the boundary, 1 once far enough inside the plate.
        //
        // Every quantity below is asymmetric - it depends on which plate the cell belongs to - and so
        // every one of them steps discontinuously at the boundary if taken from the nearest plate alone.
        // Continents then come out visibly polygonal, with a straight cliff along every plate edge, which
        // is the single most recognisable failure of a Voronoi-based generator and the reason the
        // architecture document warns about it twice. Evaluating each quantity from *both* plates'
        // points of view and cross-fading on boundary distance makes all of them continuous: at the
        // boundary both sides compute the same average, because the average does not care which plate is
        // called "own".
        val fade = PolylineFeature.smoothstep(distance / (spacing * BOUNDARY_BLEND))

        var z: Double
        var age: Double
        var caught = 0.0
        var rise: Double
        var oceanic: Double

        if (otherId >= 0) {
          val other = plates.plates[otherId]
          val contact = plates.contact(own.id, other.id)

          // Orogens are measured in *absolute* metres - a continent-continent fold belt falls off over 190 km,
          // an arc peaks 70 km inland of its trench - because that is how wide those things are on Earth. On a
          // world narrower than one fold belt that stops describing a mountain range and starts describing the
          // whole map: every cell is near a convergent boundary, nothing is ever far enough away for the
          // falloff to bite, and the continents come out as a single plateau at range height, which is above
          // the treeline for most of it and behind a rain shadow for all of it.
          //
          // So the *world* is scaled instead of the numbers: pretending a cell is `detailScale` times further
          // from the boundary than it is makes the range proportionally the size it would be on the reference
          // world, keeping the peak where the collision actually is and putting lowland back between them.
          // Exactly one at 512 km and above, so the world every constant here was tuned on does not move.
          val orogenicDistance = distance * ctx.config.detailScale

          z = blend(
            own.baseElevation + Orogeny.elevationAt(contact, own, other, orogenicDistance) * orogenicHeight,
            other.baseElevation + Orogeny.elevationAt(contact, other, own, orogenicDistance) * orogenicHeight,
            fade
          )
          rise = blend(
            Orogeny.upliftAt(contact, own, other, orogenicDistance),
            Orogeny.upliftAt(contact, other, own, orogenicDistance),
            fade
          )
          age = blend(own.age, other.age, fade)
          oceanic = blend(own.oceanicity, other.oceanicity, fade)
          caught = Orogeny.orogenicIntensity(contact, orogenicDistance)

          // Oceanic crust is created at a ridge and ages as it spreads away from it, which is why the
          // sea floor gets deeper and older with distance from the mid-ocean ridge.
          if (contact.type == BoundaryType.DIVERGENT && (own.isOceanic || other.isOceanic)) {
            age *= 1.0 - exp(-distance / RIDGE_AGE_SCALE)
          }
        } else {
          z = own.baseElevation
          age = own.age
          oceanic = own.oceanicity
          rise = if (own.isOceanic) 0.0 else Orogeny.INTERIOR_UPLIFT
        }

        crustAge.data[i] = age
        oceanicity.data[i] = oceanic

        // Ridged noise for crests, plain fbm for the broad undulation underneath it. Both are scaled
        // by how old the plate is: an ancient craton has had a billion years of erosion and is smooth,
        // young crust is rough. Using the blended age keeps the roughness continuous too.
        val roughness = 1.0 - AGE_SMOOTHING * age
        val crest = Noise.ridged(
          reliefSeed, worldX / params.reliefWavelength, worldY / params.reliefWavelength, RELIEF_OCTAVES
        )
        val undulation = Noise.fbm(
          undulationSeed,
          worldX / (params.reliefWavelength * UNDULATION_FACTOR),
          worldY / (params.reliefWavelength * UNDULATION_FACTOR),
          UNDULATION_OCTAVES
        )

        // The ridged field averages around RIDGED_MEAN rather than zero, so subtract it: otherwise
        // every relief amplitude change would also silently move the whole continent up or down.
        z += (crest - RIDGED_MEAN) * (params.interiorRelief + params.orogenicRelief * caught) * roughness
        z += undulation * params.interiorRelief * roughness

        // Swell at a wavelength longer than a plate, and completely independent of where the plates are.
        //
        // This is what stops coastlines being plate-shaped. Every other term in this stage is derived
        // from the plate layout, so without it the shoreline is a contour of the plate diagram and reads
        // as a polygon however much the boundaries themselves are warped - the eye finds the Voronoi cell
        // from the coastline, not from the boundary. Amplitude has to be comparable to the difference
        // between a continental interior and the shelf around it, or it will not move a shoreline at all.
        z += Noise.fbm(
          continentSeed,
          worldX / (spacing * CONTINENT_WAVELENGTH),
          worldY / (spacing * CONTINENT_WAVELENGTH),
          CONTINENT_OCTAVES
        ) * params.continentalSwell

        elevation.data[i] = z
        uplift.data[i] = rise
        intensity.data[i] = caught
      }
    }
    }
    }

    val cones = Timings.measure("tectonics.hotspots") {
      addHotspotChains(ctx, region, bounds, plates, spacing, elevation)
    }

    // The ocean margin is decided here, before the land fraction is normalised against the interior and well
    // before anything downstream runs. That ordering is the whole point of putting it in this stage: erosion,
    // hydrology, biomes and settlement all see nothing but deep water at the world edge, so no river tries to
    // drain across the seam and no town gets founded on ground a player would walk off.
    val border = OceanBorder.of(
      ctx.config, params.oceanBorderDepth, region, metres, region.width, params.oceanBorderWobble
    )
    Timings.measure("tectonics.normalise") {
      normaliseLandFraction(elevation, ctx.config.seaLevel, border)
    }
    border.applyTo(elevation, ctx.config.seaLevel)

    upliftDryLand(elevation, uplift, crustAge, ctx.config.seaLevel)

    val hardness = Timings.measure("tectonics.hardness") {
      rockHardness(ctx, region, elevation, crustAge, oceanicity, intensity)
    }
    val faults = Timings.measure("tectonics.faults") { traceFaults(plateId, region, plates) }

    return StageResult(
      layers = listOf(
        elevation.toLayer(LayerId.BEDROCK_ELEVATION, region),
        plateId.toLayer(LayerId.PLATE_ID, region),
        hardness.toLayer(LayerId.ROCK_HARDNESS, region),
        crustAge.toLayer(LayerId.CRUST_AGE, region),
        uplift.toLayer(LayerId.UPLIFT, region)
      ),
      features = faults + hotspotMarkers(cones)
    )
  }

  /**
   * One marker per cone the hotspot pass stamped.
   *
   * Emitting these changes no terrain and consumes no random draw, so [version] deliberately stays where it is
   * and every world generates byte-identical ground to before. That is worth stating rather than leaving to be
   * noticed: [version] reaches `GenContext.rng`, so bumping it here would reseed the plates and move every
   * mountain in the world for the sake of recording where the volcanoes already were.
   *
   * The ids come from a **block** allocator rather than a second [FeatureIds.allocator]. Two plain allocators in
   * one stage both start at ordinal zero and mint duplicate [FeatureId]s, which collapse silently in the feature
   * store's map instead of failing. Block 0 for the faults reproduces their previous ids exactly, since a block
   * allocator at block 0 issues the same ordinals a plain one did.
   */
  private fun hotspotMarkers(cones: List<HotspotCone>): List<VectorFeature> {
    val nextId = FeatureIds.blockAllocator(id, HOTSPOT_ID_BLOCK)

    return cones.map { cone ->
      PointMarker(
        id = nextId(),
        kind = FeatureKind.HOTSPOT,
        position = cone.centre,
        attributes = StationTable.Builder(1)
          .channel(CHANNEL_CHAIN_INDEX) { cone.chainIndex.toDouble() }
          .channel(CHANNEL_CONE_HEIGHT) { cone.height }
          .channel(CHANNEL_CONE_RADIUS) { cone.radius }
          .channel(CHANNEL_OCEANIC) { if (cone.oceanic) 1.0 else 0.0 }
          .build()
      )
    }
  }

  /**
   * One stamped cone, as the volcanism stage needs to see it.
   *
   * [chainIndex] is the interesting field: 0 is the cone sitting over the plume now and every step is further
   * along the track and further into the past. `HOTSPOT_DECAY` already says the older ones are lower and more
   * eroded; the index is what lets a later stage say which of them still has an open crater.
   */
  class HotspotCone(
    val centre: Vec2d,
    val chainIndex: Int,
    val height: Double,
    val radius: Double,
    val oceanic: Boolean
  )

  /**
   * Volcanic chains: a hotspot is fixed in the mantle while the plate above it moves, so it stamps a
   * line of progressively older, more eroded cones onto the drifting plate.
   *
   * The cone's *shape* is applied to the raster rather than pushed into the vector tier, because a volcanic
   * island is 30-80 km across - comfortably wider than the three coarse cells that are the threshold for
   * needing a feature. Its *location* is returned as well, which is a different question: the shape argument
   * says nothing about whether a later stage should be able to find the volcano, and for a long time none
   * could. See [hotspotMarkers].
   */
  private fun addHotspotChains(
    ctx: GenContext,
    region: CellRegion,
    bounds: Aabb,
    plates: PlateSet,
    spacing: Double,
    elevation: Grid
  ): List<HotspotCone> {
    val cones = ArrayList<HotspotCone>()
    val metres = region.resolution.metresPerCell
    val rng = ctx.rng(HOTSPOT_STREAM)

    // Chain *origins* follow the plates: denser plates mean more hotspots, which is right - a hotspot is a
    // mantle plume and there are more of them under a more finely divided lithosphere.
    val origins = PoissonDisk.sample(bounds, params.hotspotSpacing ?: spacing * HOTSPOT_SPACING_FACTOR, rng)

    // The islands themselves do *not*. An island's size is set by how much magma one plume delivers, which has
    // nothing to do with how big the plate above it is - and tying it to the plate spacing means a world with
    // finer plates silently gets smaller islands, which is not a physical consequence of anything. Floored at
    // the unscaled plate spacing so that shrinking the floor for small worlds adds island *chains* without
    // shrinking the islands, and so that nothing changes at all on a world where the floor never bound.
    val islandScale = max(spacing, MIN_PLATE_SPACING)

    val sample = DoubleArray(3)
    val scratch = DoubleArray(4)

    for (origin in origins) {
      plates.sampleInto(origin.x, origin.y, sample, scratch)
      val plate = plates.plates[sample[0].toInt()]

      // The chain trails *behind* the plate's motion: the island over the hotspot now is the youngest.
      val drift = plate.drift.normalized()
      if (drift.lengthSquared == 0.0) continue

      val step = islandScale * HOTSPOT_STEP_FACTOR
      val peak = if (plate.isOceanic) OCEANIC_HOTSPOT_PEAK else CONTINENTAL_HOTSPOT_PEAK

      // The track curves, because a chain laid along a fixed heading is a ruled line and reads as one.
      //
      // At these factors the cones overlap - 7.5 km across at 5.5 km spacing - so a chain is not a row of
      // separate islands but one continuous ridge about 38 km long. In open water that is a good island arc.
      // Crossing a continental interior, which is where the denser plates put a lot of them, it is a
      // perfectly straight 1,500 m wall running a third of the way across the landmass, bare rock capping it
      // and biome bands ruled off either side. It was the most conspicuous straight edge left on the map once
      // the coastline stopped being a rectangle.
      //
      // Letting the heading wander is also the more truthful model: a hotspot track records the plate's
      // motion over tens of millions of years, and that motion changes. The bend in the Hawaii-Emperor chain
      // is sixty degrees. A random walk in heading gives the same character - locally straight, globally
      // curved - for one extra term.
      var heading = atan2(drift.y, drift.x)
      var centre = origin

      for (k in 0 until params.hotspotChainLength) {
        heading += (rng.nextDouble() - 0.5) * 2.0 * HOTSPOT_CURVATURE
        val direction = Vec2d(cos(heading), sin(heading))

        // Jitter each island's own position along and across the track, not just the chain's spacing.
        // A single spacing per chain produces a line of perfectly evenly spaced dots, and evenly spaced
        // is the one thing a volcanic chain never is - the regularity reads as a rendering artefact
        // rather than as islands.
        val advance = step * (0.75 + rng.nextDouble() * 0.5)
        val sideways = direction.perpendicular() * (step * (rng.nextDouble() - 0.5) * HOTSPOT_WANDER)
        centre = centre - direction * advance + sideways
        if (!bounds.contains(centre.x, centre.y)) continue

        val height = peak * exp(-k * HOTSPOT_DECAY) * (0.7 + rng.nextDouble() * 0.6)
        val radius = islandScale * HOTSPOT_RADIUS_FACTOR * (0.75 + rng.nextDouble() * 0.5)

        stampCone(elevation, region, metres, centre, radius, height)
        // Recorded after the stamp and after the bounds `continue` above, so the draw order is untouched: an
        // out-of-bounds cone still consumes three draws and not five, and every island downstream of it in the
        // chain lands exactly where it did before.
        cones.add(HotspotCone(centre, k, height, radius, plate.isOceanic))
      }
    }

    return cones
  }

  private fun stampCone(
    elevation: Grid,
    region: CellRegion,
    metres: Double,
    centre: Vec2d,
    radius: Double,
    height: Double
  ) {
    val minCellX = max(0, ((centre.x - radius) / metres).toInt() - region.minX - 1)
    val maxCellX = min(region.width - 1, ((centre.x + radius) / metres).toInt() - region.minX + 1)
    val minCellY = max(0, ((centre.y - radius) / metres).toInt() - region.minY - 1)
    val maxCellY = min(region.height - 1, ((centre.y + radius) / metres).toInt() - region.minY + 1)

    for (y in minCellY..maxCellY) {
      val worldY = (region.minY + y + 0.5) * metres
      for (x in minCellX..maxCellX) {
        val worldX = (region.minX + x + 0.5) * metres
        val t = Vec2d(worldX, worldY).distanceTo(centre) / radius
        if (t >= 1.0) continue

        // A pointed cone rather than a smoothstep dome: volcanoes have summits. The exponent above 1
        // still gives zero slope at the toe, so it blends into the sea floor without a rim.
        elevation.data[elevation.index(x, y)] += height * (1.0 - t).pow(CONE_SHARPNESS)
      }
    }
  }

  /**
   * Shifts the whole heightfield so that [params].targetLandFraction **of the whole world** ends up above sea
   * level once [border] has been applied.
   *
   * ### Why this is not a quantile any more
   *
   * It used to put the target quantile of the *interior* cells at sea level, and then the margin was applied on
   * top and drowned some of what had just been counted as land: all of the margin proper - excluded from the
   * count, so harmless - but also about half of the coastal shelf band beyond it, which was counted. A 128 km
   * world asking for 0.32 got 0.30 in bedrock and 0.28 after erosion.
   *
   * Being a couple of points out would be tolerable. Being **world-size dependent** is not: the same 0.32 was
   * nearly honest on a 512 km world, where the margin is a fiftieth of the area rather than a sixth. So the
   * number meant something different on every world, which defeats the point of having it - it exists so that
   * seeds are comparable and most of them usable.
   *
   * ### The search
   *
   * The finished land count is monotone non-decreasing in the shift, so bisect on the shift directly. Cells the
   * margin cannot touch keep the histogram - their answer is a pure function of the shift, so one `O(N)` pass
   * serves every iteration - and the band the margin *can* touch is re-evaluated per iteration through
   * [OceanBorder.heightAt], which is the same code that will actually be applied. The band is about a fifth of
   * a small world and a twentieth of a large one, so this costs a small multiple of one pass, not twenty.
   *
   * Exact in bedrock. Erosion and deposition then move the shoreline by however much the seed's rivers build,
   * which is a legitimate property of the seed rather than an error - see `Invariants.checkLandFraction`.
   */
  private fun normaliseLandFraction(elevation: Grid, seaLevel: Double, border: OceanBorder) {
    val low = elevation.min()
    val high = elevation.max()
    if (high - low < 1e-9) return

    val bins = IntArray(QUANTILE_BINS)
    val scale = QUANTILE_BINS / (high - low)
    val band = ArrayList<Int>()

    for (i in elevation.data.indices) {
      if (border.isInBlend(i)) {
        band.add(i)
      } else {
        bins[((elevation.data[i] - low) * scale).toInt().coerceIn(0, QUANTILE_BINS - 1)]++
      }
    }

    // Cumulative from the top, so `landOutsideBand(threshold)` is one lookup rather than a scan.
    val atOrAbove = LongArray(QUANTILE_BINS + 1)
    for (bin in QUANTILE_BINS - 1 downTo 0) {
      atOrAbove[bin] = atOrAbove[bin + 1] + bins[bin]
    }

    /** Cells outside the margin's reach that a shift of [shift] leaves above sea level. */
    fun landOutsideBand(shift: Double): Double {
      // A cell is land when `z + shift > seaLevel`, i.e. `z > seaLevel - shift`.
      val threshold = seaLevel - shift
      val position = (threshold - low) * scale
      if (position < 0.0) return atOrAbove[0].toDouble()
      if (position >= QUANTILE_BINS) return 0.0

      // Interpolated inside the straddling bin, so the search converges smoothly rather than in bin steps.
      val bin = position.toInt()
      val within = position - bin
      return atOrAbove[bin + 1] + bins[bin] * (1.0 - within)
    }

    fun landAt(shift: Double): Double {
      var land = landOutsideBand(shift)
      for (i in band) {
        if (border.heightAt(i, elevation.data[i] + shift, seaLevel) > seaLevel) land++
      }
      return land
    }

    val wanted = params.targetLandFraction * elevation.data.size

    // Bounds: enough to drown everything, and enough to lift everything the margin will allow. The margin's
    // own depth is added to the top so that the upper bound really is unreachable-or-better.
    var lowShift = seaLevel - high - 1.0
    var highShift = seaLevel - low + params.oceanBorderDepth + 1.0

    // If even the highest shift cannot reach the target, the world is asking for more land than its forced
    // margin leaves room for. Take the most it can give rather than throwing: a small world with a wide
    // margin legitimately has a ceiling, and refusing to generate it would be worse than approximating.
    if (landAt(highShift) < wanted) {
      applyShift(elevation, highShift)
      return
    }

    repeat(SHIFT_SEARCH_STEPS) {
      val middle = (lowShift + highShift) / 2.0
      if (landAt(middle) < wanted) lowShift = middle else highShift = middle
    }

    applyShift(elevation, (lowShift + highShift) / 2.0)
  }

  private fun applyShift(elevation: Grid, shift: Double) {
    for (i in elevation.data.indices) {
      elevation.data[i] += shift
    }
  }

  /**
   * Gives every cell that ended up above sea level at least an interior plate's worth of uplift.
   *
   * **The reason a land-heavy world does not come out flat**, and it is a physical statement rather than a
   * cosmetic one: crust that stands above sea level is crust that is being held up, and rock that is being
   * held up is rock that erosion has something to cut into. Stream power is `U - K A^m S`; where `U` is zero
   * the only steady state is a plane, so the forty-five erosion timesteps *remove* whatever relief the noise
   * put there instead of organising it into valleys and ridges.
   *
   * Which matters because [oceanicShare] and [targetLandFraction] cannot be kept perfectly in step across
   * every seed. Wherever the waterline lands a little inside the oceanic cluster, the land that surfaces is
   * ex-seafloor with `uplift = 0` (see `Plates.upliftAt`), and without this it stays a featureless shelf -
   * unmistakable on the map as a smooth pale plain with a coastline and nothing else.
   *
   * Runs after the margin, on the final heights, for the same reason [rockHardness] does: "above sea level" is
   * not a question that can be asked before the land fraction is fixed. Old crust gets less, as it does in
   * `Plates`: a craton has finished rising.
   */
  private fun upliftDryLand(elevation: Grid, uplift: Grid, crustAge: Grid, seaLevel: Double) {
    for (i in elevation.data.indices) {
      val above = elevation.data[i] - seaLevel
      if (above <= 0.0) continue

      // Ramped in over the first couple of hundred metres rather than switched on at the waterline: a step in
      // uplift along a contour is a step in erosion rate along a contour, and erosion turns that into an
      // escarpment that follows the coast - a landform made by an `if`.
      val ramp = PolylineFeature.smoothstep((above / DRY_UPLIFT_RAMP).coerceIn(0.0, 1.0))
      val floor = Orogeny.INTERIOR_UPLIFT * (1.0 - AGE_SMOOTHING * crustAge.data[i]) * ramp

      if (floor > uplift.data[i]) uplift.data[i] = floor
    }
  }

  /**
   * Rock hardness, which is what makes erosion produce interesting terrain instead of uniform mush.
   *
   * Runs as a second pass because it reads the *normalised* elevation: whether a cell is a
   * sedimentary basin is a question about how low it is relative to sea level, and sea level only
   * means anything after the land fraction has been fixed.
   */
  private fun rockHardness(
    ctx: GenContext,
    region: CellRegion,
    elevation: Grid,
    crustAge: Grid,
    oceanicity: Grid,
    intensity: Grid
  ): Grid {
    val metres = region.resolution.metresPerCell
    val seed = GenRng.mix64(ctx.seed xor HARDNESS_SALT)
    val seaLevel = ctx.config.seaLevel
    val hardness = Grid(region.width, region.height)

    for (y in 0 until region.height) {
      val worldY = (region.minY + y + 0.5) * metres
      for (x in 0 until region.width) {
        val i = hardness.index(x, y)
        val above = elevation.data[i] - seaLevel
        val caught = intensity.data[i]
        val oceanic = oceanicity.data[i]

        // Basalt is tougher than the average continental cover but softer than a craton's granite.
        var h = 0.44 + 0.14 * oceanic

        // Cratonisation: old continental crust is granite and gneiss, and it does not yield.
        h += crustAge.data[i] * 0.30

        // Orogens expose intrusives and metamorphics.
        h += caught * 0.16

        // Sedimentary basins: low, quiet continental ground, filled with soft mudstone and sandstone.
        // This is where erosion carves badlands and wide floodplains rather than gorges.
        //
        // Every factor is a smooth ramp rather than a threshold. A threshold on elevation would put a
        // hardness cliff along a contour line, and erosion would then turn that contour into a visible
        // escarpment - a landform created by a comparison operator rather than by geology.
        val basin = (1.0 - oceanic) *
            PolylineFeature.smoothstep((BASIN_ELEVATION - above) / BASIN_ELEVATION) *
            PolylineFeature.smoothstep((BASIN_QUIET - caught) / BASIN_QUIET)
        h -= 0.26 * basin

        h += Noise.fbm(
          seed, (region.minX + x + 0.5) * metres / HARDNESS_WAVELENGTH, worldY / HARDNESS_WAVELENGTH, 3
        ) * 0.14

        hardness.data[i] = h.coerceIn(0.05, 0.98)
      }
    }

    return hardness
  }

  /**
   * Cross-fade between a quantity computed from this side of a boundary and the same quantity computed
   * from the other side.
   *
   * At `fade == 0` - on the boundary - both plates produce the same value, because the mean of the two
   * does not depend on which one is called "own". That symmetry is what makes the field continuous.
   */
  private fun blend(own: Double, other: Double, fade: Double): Double {
    val w = 0.5 + 0.5 * fade
    return own * w + other * (1.0 - w)
  }

  private fun traceFaults(
    plateId: IntGrid,
    region: CellRegion,
    plates: PlateSet
  ): List<MarkerFeature> {
    // Block 0, which issues the same ordinals a plain allocator did, so no fault id moves. The block form is
    // what keeps the hotspot markers from minting duplicates of these - see [hotspotMarkers].
    val nextId = FeatureIds.blockAllocator(id, FAULT_ID_BLOCK)

    return BoundaryTracer.trace(plateId, region).map { trace ->
      val contact = plates.contact(trace.plateA, trace.plateB)
      MarkerFeature(
        id = nextId(),
        kind = FeatureKind.FAULT,
        centerline = trace.line,
        stations = faultStations(trace.line, contact)
      )
    }
  }

  /**
   * Boundary attributes as station channels.
   *
   * All three are constant along the line, because the classification is a property of the plate
   * *pair* rather than of a position on the boundary. They are stations anyway rather than fields on a
   * bespoke type, so that a later stage which does vary something along a fault - vent spacing, say -
   * adds a channel instead of a class. Interpolating a constant channel is exact, so nothing is lost.
   */
  private fun faultStations(line: Polyline, contact: BoundaryContact): StationTable =
    StationTable.Builder(line.vertexCount)
      .channel(CHANNEL_BOUNDARY_TYPE) { contact.type.ordinal.toDouble() }
      .channel(CHANNEL_CONVERGENCE) { contact.convergence }
      .channel(CHANNEL_STRENGTH) { contact.strength }
      .build()

  /**
   * Plate spacing when none is chosen: about five plates across the short edge, floored.
   *
   * ### Why the floor is scaled
   *
   * [MIN_PLATE_SPACING] is a statement about real plates - fifty kilometres is about the smallest thing that
   * behaves like one - and on any world of a decent size it never binds, because a fifth of the short edge is
   * larger. On a *small* world it binds hard and distorts everything: a 128 km world wants 25.6 km and is
   * clamped up to 50 km, which leaves it six to nine plates, a boundary cross-fade a fifth of the map wide, and
   * a continental swell (at `spacing * CONTINENT_WAVELENGTH`) whose wavelength is 75 km - **1.7 lobes across the
   * whole world**, which is a tilt rather than a landscape. That is a large part of why a small world's interior
   * comes out as one featureless plain.
   *
   * [WorldConfig.scaleByLength] is the existing answer to exactly this class of problem: it shrinks a threshold
   * that gates a feature on the world being big enough. Applying it to the floor gives a 128 km world 12.5 km
   * of headroom, so it keeps its natural 25.6 km spacing and gets around twenty-five plates.
   *
   * ### And it cannot run away on a large world
   *
   * `detailScale` is `(512 km / shortEdge).coerceIn(1.0, 8.0)`, so it is **exactly 1.0 for every world at or
   * above 512 km** and the floor there is the unscaled 50 km, unchanged. It would not have mattered anyway -
   * 512/5 is 102 km and 4096/5 is 819 km, both above the floor - but the two facts together mean this change is
   * provably a no-op for anything the reference world's constants were tuned against. `the plate spacing of a
   * reference world is unchanged` in `TectonicsTest` holds the line.
   */
  internal fun defaultSpacing(config: WorldConfig, width: Double, height: Double): Double =
    (min(width, height) / 5.0).coerceIn(config.scaleByLength(MIN_PLATE_SPACING), MAX_PLATE_SPACING)

  companion object {
    val ID = StageId("tectonics")

    /** Station channels on a [FeatureKind.FAULT] marker. */
    const val CHANNEL_BOUNDARY_TYPE = "boundary_type"
    const val CHANNEL_CONVERGENCE = "convergence"
    const val CHANNEL_STRENGTH = "strength"

    /** Station channels on a [FeatureKind.HOTSPOT] marker. */
    const val CHANNEL_CHAIN_INDEX = "chain_index"
    const val CHANNEL_CONE_HEIGHT = "cone_height"
    const val CHANNEL_CONE_RADIUS = "cone_radius"
    const val CHANNEL_OCEANIC = "oceanic"

    /**
     * Feature id blocks, one per emitter in this stage.
     *
     * Two plain [FeatureIds.allocator] calls would both start at ordinal zero and mint duplicate ids, which
     * `FeatureStore` collapses in a map rather than rejecting - a silent loss. Faults keep block 0 so their ids
     * are unchanged from before there was a second emitter.
     */
    private const val FAULT_ID_BLOCK = 0
    private const val HOTSPOT_ID_BLOCK = 1

    /**
     * The boundary type a fault marker carries, or null if it carries none.
     *
     * Here rather than in each reader because there are now three of them - ore genesis wants convergent
     * boundaries, closed basins want divergent ones - and the alternative is three copies of "resolve the
     * channel, sample it at zero, turn a double into an ordinal". The ordinal round trip in particular is the
     * kind of thing that stays correct in three places right up until the enum gains a member.
     *
     * Null rather than a throw for a marker with no such channel: [FeatureKind.FAULT] is the only kind that
     * carries one, and a caller filtering a mixed query should not have to guard against its own filter.
     */
    fun boundaryTypeOf(fault: MarkerFeature): BoundaryType? {
      val stations = fault.stations ?: return null
      val channel = runCatching { stations.channel(CHANNEL_BOUNDARY_TYPE) }.getOrNull() ?: return null
      return BoundaryType.entries.getOrNull(stations.sample(channel, 0.0).toInt())
    }

    private const val PLATE_STREAM = 1L
    private const val HOTSPOT_STREAM = 2L

    private const val RELIEF_SALT = 0x51D4B2A7C3E19F0L
    private const val UNDULATION_SALT = 0x2C7E9A1F5B3D840L
    private const val HARDNESS_SALT = 0x6B1F3C8E27A594DL
    private const val CONTINENT_SALT = 0x74E82B15C6A39D0L

    private const val RELIEF_OCTAVES = 6
    private const val UNDULATION_OCTAVES = 4
    private const val CONTINENT_OCTAVES = 3

    /** Wavelength of the regional swell, as a multiple of plate spacing. Longer than a plate. */
    private const val CONTINENT_WAVELENGTH = 1.5

    /** Wavelength of the broad undulation, as a multiple of the crest wavelength. */
    private const val UNDULATION_FACTOR = 5.0

    /** Mean of [Noise.ridged] at the default gain; subtracted so relief has no net offset. */
    private const val RIDGED_MEAN = 0.33

    /** How much of the relief an ancient craton has lost. */
    private const val AGE_SMOOTHING = 0.62

    /**
     * Width of the cross-fade at a plate boundary, as a fraction of plate spacing.
     *
     * Generous on purpose. A real continental margin grades into the ocean floor over a hundred
     * kilometres or more, so blending over roughly a third of the plate spacing is both what removes
     * the polygon edges and what produces a continental shelf rather than a wall.
     */
    private const val BOUNDARY_BLEND = 0.45

    /** Distance over which oceanic crust ages away from a spreading ridge, in metres. */
    private const val RIDGE_AGE_SCALE = 300_000.0

    private const val MIN_PLATE_SPACING = 50_000.0
    private const val MAX_PLATE_SPACING = 700_000.0

    private const val HOTSPOT_SPACING_FACTOR = 2.2
    private const val HOTSPOT_STEP_FACTOR = 0.11
    private const val HOTSPOT_RADIUS_FACTOR = 0.075
    private const val HOTSPOT_DECAY = 0.4

    /** How far an island may sit off the hotspot track, as a fraction of the chain step. */
    private const val HOTSPOT_WANDER = 0.8

    /**
     * Largest heading change per island, in radians. A random walk, so the chain curves rather than turns.
     *
     * 0.22 is about twelve degrees a step; over a seven-island chain the track typically swings some thirty
     * degrees and can reach ninety. Much more and the chain doubles back on itself and stops reading as a
     * track at all, which loses the thing an island arc is *for* - it is the one feature on the map that
     * tells a player which way the plate under them is moving.
     */
    private const val HOTSPOT_CURVATURE = 0.22
    private const val OCEANIC_HOTSPOT_PEAK = 3_800.0
    private const val CONTINENTAL_HOTSPOT_PEAK = 1_500.0
    private const val CONE_SHARPNESS = 1.6

    private const val BASIN_ELEVATION = 280.0
    private const val BASIN_QUIET = 0.15
    private const val HARDNESS_WAVELENGTH = 26_000.0

    /**
     * Metres of elevation over which the dry-land uplift floor ramps in from nothing.
     *
     * Comparable to the coastal relief it has to not disturb: short enough that an inland plain is properly
     * uplifted, long enough that no escarpment forms along the shoreline.
     */
    private const val DRY_UPLIFT_RAMP = 250.0

    private const val QUANTILE_BINS = 4096

    /**
     * Bisection steps for the land-fraction shift.
     *
     * Twenty halvings take a range of a few thousand metres to under a centimetre, which is far finer than a
     * heightfield means anything at, and each step costs only the margin band rather than the whole world.
     */
    private const val SHIFT_SEARCH_STEPS = 20
  }
}
