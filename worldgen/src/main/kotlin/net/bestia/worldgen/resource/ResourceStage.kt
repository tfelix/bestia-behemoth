package net.bestia.worldgen.resource

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.bio.VegetationStage
import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.IntLayer
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
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.DistanceTransform
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.PoissonDisk
import net.bestia.worldgen.geo.BoundaryType
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * How the ore in a deposit splits between the three grades, and what each grade is worth.
 *
 * The whole of the tonnage arithmetic rests on [meanYieldKg]. A deposit is drawn as a number of tons, and the
 * body that holds them is sized by dividing that by the yield of an average ore voxel - so retuning these six
 * numbers changes how big every orebody in the world is, not just how much falls out of it.
 *
 * The default mix is five small to three medium to one rich, yielding half a kilogram, one kilogram and five.
 * Rich voxels are therefore an eleventh of the ore and just under half of the metal, which is what makes the
 * dense middle of a body worth digging out rather than merely worth finding.
 */
data class GradeMix(

  val smallWeight: Double = 5.0,
  val mediumWeight: Double = 3.0,
  val richWeight: Double = 1.0,

  val smallYieldKg: Double = 0.5,
  val mediumYieldKg: Double = 1.0,
  val richYieldKg: Double = 5.0
) : Params {

  init {
    require(smallWeight >= 0.0) { "smallWeight must not be negative, was $smallWeight" }
    require(mediumWeight >= 0.0) { "mediumWeight must not be negative, was $mediumWeight" }
    require(richWeight >= 0.0) { "richWeight must not be negative, was $richWeight" }
    require(smallWeight + mediumWeight + richWeight > 0.0) { "at least one grade must have some weight" }
    require(smallYieldKg > 0.0) { "smallYieldKg must be positive, was $smallYieldKg" }
    require(mediumYieldKg > 0.0) { "mediumYieldKg must be positive, was $mediumYieldKg" }
    require(richYieldKg > 0.0) { "richYieldKg must be positive, was $richYieldKg" }
  }

  private val totalWeight get() = smallWeight + mediumWeight + richWeight

  /** Kilograms an average ore voxel yields. The constant the whole tonnage model divides by. */
  val meanYieldKg: Double
    get() = (smallWeight * smallYieldKg + mediumWeight * mediumYieldKg + richWeight * richYieldKg) / totalWeight

  /** What a voxel of this grade drops. */
  fun yieldKgOf(grade: OreGrade) = when (grade) {
    OreGrade.SMALL -> smallYieldKg
    OreGrade.MEDIUM -> mediumYieldKg
    OreGrade.RICH -> richYieldKg
  }

  /**
   * The grade for a roll on `[0,1)`.
   *
   * Rolled independently of where in the body the voxel sits, on purpose. Biasing rich voxels toward the
   * middle would look better and would make the ratio a function of the body's shape, which is the one thing
   * the tonnage arithmetic assumes it is not.
   */
  fun gradeAt(roll: Double): OreGrade {
    val scaled = roll.coerceIn(0.0, 1.0) * totalWeight
    return when {
      scaled < smallWeight -> OreGrade.SMALL
      scaled < smallWeight + mediumWeight -> OreGrade.MEDIUM
      else -> OreGrade.RICH
    }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    smallWeight = source.double("smallWeight", smallWeight),
    mediumWeight = source.double("mediumWeight", mediumWeight),
    richWeight = source.double("richWeight", richWeight),
    smallYieldKg = source.double("smallYieldKg", smallYieldKg),
    mediumYieldKg = source.double("mediumYieldKg", mediumYieldKg),
    richYieldKg = source.double("richYieldKg", richYieldKg)
  )

  override fun digest() = ParamsDigest()
    .put("smallWeight", smallWeight)
    .put("mediumWeight", mediumWeight)
    .put("richWeight", richWeight)
    .put("smallYieldKg", smallYieldKg)
    .put("mediumYieldKg", mediumYieldKg)
    .put("richYieldKg", richYieldKg)
}

/** Tuning for [ResourceStage]. */
data class ResourceParams(

  /**
   * Target spacing between candidate sites of one resource type, in metres.
   *
   * Candidates are then *thinned* by the suitability field, so this sets the finest possible spacing rather
   * than the actual density: a type whose geology only occurs in one corner of the world ends up with
   * deposits only there, however small this is. Ore candidates are thinned a second time by [oreSeparation],
   * so this can be tighter than the spacing actually wanted on the ground.
   */
  val candidateSpacing: Double = 26_000.0,

  /**
   * Fewest candidate sites the rarest ore gets across the short edge of the world.
   *
   * A floor on how sparse a small world may be, and the only place this stage looks at the world's size.
   * Without it the 128 km development world had **four or five of the seven ores missing entirely**: gold
   * samples at 62 km, which is two spacings across such a map, so it got about six candidate sites and
   * suitability thinned those to well under one. Silver came out absent in two worlds of every three. That is
   * not scarcity, it is a resource that does not exist, and a player can cross the whole map and prove it.
   *
   * Applied as **one factor to every ore at once**, computed from the coarsest spacing any of them asks for,
   * so their relative rarity is untouched - clamping each ore separately would make gold as common as copper
   * on exactly the worlds that needed the help.
   *
   * It stops binding at about 375 km, so a world of the size this generator is really for never sees it, and
   * `min(1.0, ...)` makes that a bit-identical no-op rather than a rounding difference. That is the important
   * half of the design: `WorldConfig.scaleByLength` would have been the obvious tool and is the wrong one -
   * it holds the *count* constant, which is why `CaveStage` refuses it and cites this stage for doing the
   * same. Ore is a property of the rock and scales with area. This is a playability floor under that, not a
   * different density model.
   */
  val minSitesAcross: Double = 6.0,

  /**
   * Deposits of each mineable ore every world gets, however small or however poor its geology.
   *
   * The hard guarantee behind "every world has every ore". [minSitesAcross] tightens the *sampling* so a small
   * world is offered enough sites, but sampling stays a lottery and a lottery has a losing ticket: the ore with
   * the strictest geology still came out absent on some seeds. This is the floor under that, filled from the
   * best ground the world actually has - see `guarantee`, which explains why topping up is causally honest
   * rather than a sprinkle.
   *
   * A floor of last resort, not the main mechanism. Above it, scarcity still comes from geology and spacing:
   * a 512 km world naturally holds thirty-odd copper deposits against two or three of mithrandium, and nothing
   * here touches that.
   */
  val minDepositsPerOre: Int = 3,

  /**
   * Least distance between any two mineable deposits, whatever their type, in metres.
   *
   * The reason the stage has a dispersal pass at all. Each resource samples its own Poisson set independently,
   * so without this a copper body and a silver body can land a hundred metres apart - and a player who finds
   * one has found both, which is the opposite of a reason to cross the map. Enforced rarest ore first, so
   * mithrandium picks its ground before iron does.
   */
  val oreSeparation: Double = 12_000.0,

  /**
   * How far apart two orebodies must be as a multiple of their combined radii.
   *
   * The physical half of the rule [oreSeparation] is the gameplay half of: two bodies that intersect would
   * make one voxel two different ores, and `OreVeins` would resolve it by feature order rather than by
   * geology.
   */
  val bodyClearance: Double = 1.5,

  /** Multiplier on every deposit's tonnage. One knob for "there is too much / too little metal about". */
  val tonnageScale: Double = 1.0,

  /** How the ore inside a deposit splits between the three grades. */
  val grades: GradeMix = GradeMix(),

  /** Radius over which a deposit contributes to the resource-value field, in metres. */
  val valueRadius: Double = 42_000.0,

  /** How far placer gold is carried downstream from a lode, in metres. */
  val placerRange: Double = 90_000.0,

  /** Spacing between placer deposits along a river, in metres. */
  val placerSpacing: Double = 12_000.0
) : Params {

  init {
    require(candidateSpacing > 0.0) { "candidateSpacing must be positive, was $candidateSpacing" }
    require(valueRadius > 0.0) { "valueRadius must be positive, was $valueRadius" }
    require(placerRange >= 0.0) { "placerRange must not be negative, was $placerRange" }
    require(placerSpacing > 0.0) { "placerSpacing must be positive, was $placerSpacing" }
    require(oreSeparation > 0.0) { "oreSeparation must be positive, was $oreSeparation" }
    require(bodyClearance >= 1.0) { "bodyClearance must be at least 1, was $bodyClearance" }
    require(tonnageScale > 0.0) { "tonnageScale must be positive, was $tonnageScale" }
    require(minSitesAcross >= 1.0) { "minSitesAcross must be at least 1, was $minSitesAcross" }
    require(minDepositsPerOre >= 0) { "minDepositsPerOre must not be negative, was $minDepositsPerOre" }

    // The dispersal pass buckets candidates into cells of oreSeparation and only looks at the eight
    // neighbours, so a rejection that could reach further than one cell would not be seen.
    val reach = 2.0 * OreBody.MAX_RADIUS * bodyClearance
    require(oreSeparation >= reach) {
      "oreSeparation $oreSeparation is below $reach, the furthest two bodies can reject each other from"
    }
  }

  /**
   * How much every ore's candidate spacing is tightened on a world of this size. See [minSitesAcross].
   *
   * Driven by the *coarsest* spacing any ore asks for rather than by each ore's own, which is what keeps the
   * ratios between them intact. Returns exactly 1.0 - a genuine no-op, not a rounding difference - for any
   * world big enough to deserve its ore without help.
   *
   * @param shortEdgeMetres the shorter of the world's two extents, because an ore has to be findable along
   *   both axes and the narrow one is what limits that
   */
  fun spacingShrink(shortEdgeMetres: Double): Double {
    val floor = shortEdgeMetres / minSitesAcross
    val coarsest = candidateSpacing * MinableOre.entries.maxOf { it.spacingFactor }
    return min(1.0, floor / coarsest)
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    candidateSpacing = source.double("candidateSpacing", candidateSpacing),
    minSitesAcross = source.double("minSitesAcross", minSitesAcross),
    minDepositsPerOre = source.int("minDepositsPerOre", minDepositsPerOre),
    oreSeparation = source.double("oreSeparation", oreSeparation),
    bodyClearance = source.double("bodyClearance", bodyClearance),
    tonnageScale = source.double("tonnageScale", tonnageScale),
    grades = grades.overriddenBy(source.scope("grades")),
    valueRadius = source.double("valueRadius", valueRadius),
    placerRange = source.double("placerRange", placerRange),
    placerSpacing = source.double("placerSpacing", placerSpacing)
  )

  override fun digest() = ParamsDigest()
    .put("candidateSpacing", candidateSpacing)
    .put("minSitesAcross", minSitesAcross)
    .put("minDepositsPerOre", minDepositsPerOre)
    .put("oreSeparation", oreSeparation)
    .put("bodyClearance", bodyClearance)
    .put("tonnageScale", tonnageScale)
    .nested("grades", grades.digest().value)
    .put("valueRadius", valueRadius)
    .put("placerRange", placerRange)
    .put("placerSpacing", placerSpacing)
}

/**
 * Stage 6: mineral and surface resources, placed causally.
 *
 * Resources are *not* sprinkled. Every type here has a geological or ecological setting, and it is read
 * from what earlier stages already established rather than invented: arc metals from the convergent plate
 * boundaries the tectonics stage emitted as fault polylines, tin from old hard crust, mithrandium from the
 * roots of the oldest mountains, salt from the beds of endorheic lakes the hydrology stage identified, clay
 * from floodplain sediment, placer gold traced *downstream* of hard-rock gold along the river network.
 *
 * That last one is worth the effort on its own: it makes prospecting a real mechanic. A player who finds
 * placer gold in a river gravel can walk upstream and find the lode, because the world actually put it
 * there for that reason.
 *
 * ### Tonnage
 *
 * A deposit says how much metal is in it, in tons, and the number is true. [OreBody] holds the closed form
 * that ties a tonnage to a body radius and a concentration, and this stage runs it backwards: draw the
 * tonnage, take the concentration from the geology, solve for the radius that holds it. `OreVeins` then runs
 * the same form forwards at chunk generation, so counting every ore voxel in a body and adding up what each
 * drops comes back to the number on the marker. Before this, quantity was in "arbitrary units" and the
 * per-voxel fill probability was unrelated to it, which meant nothing about mining could be balanced.
 *
 * ### Dispersal
 *
 * Each resource samples its own Poisson set, so nothing stops two of them landing on the same hillside. A
 * dispersal pass fixes that for the mineable ores: candidates are offered rarest first and rejected if they
 * come within [ResourceParams.oreSeparation] of one already accepted. The surface resources skip it - a stand
 * of timber over a clay pit is not a problem, and making them compete would let the commonest of them crowd
 * the metals out.
 *
 * Deposits are emitted as sparse [PointMarker] features, never as a per-cell field. A world has perhaps a
 * hundred thousand of them against sixteen million cells; per-voxel ore materialises at chunk generation by
 * sampling the deposit, and is never stored.
 */
class ResourceStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: ResourceParams = ResourceParams()
) : Stage {

  override val id = ID

  override val version = 1

  override val paramsVersion
    get() = GenRng.hash(
      params.digest().value,
      ResourceType.catalogueDigest(),
      MinableOre.catalogueDigest()
    )
  override val dependencies = listOf(
    TectonicsStage.ID, ClimateStage.ID, ErosionStage.ID, HydrologyStage.ID, BiomeStage.ID,
    // For CANOPY_COVER. Timber used to be a five-way `when` over the biome, which said that every square
    // kilometre of temperate forest in the world was equally worth logging - see suitabilityFor.
    VegetationStage.ID
  )
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.RESOURCE_VALUE),
    StageOutput.Vector(FeatureKind.ORE_DEPOSIT)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val terrain = Terrain.read(ctx, region)
    val nextId = FeatureIds.allocator(id)
    val voxelSize = ctx.config.voxelSize
    val deposits = ArrayList<PointMarker>()

    // Ore first, rarest ore first within that, because the dispersal pass hands out ground in the order it is
    // offered and the scarce metals are the ones with nowhere else to go.
    //
    // Three passes, and they have to be in this order. Placement decides *where*, the guarantee fills in any
    // ore the sampler missed, and only then does sizing run - because how much metal is in one deposit is the
    // world's total for that ore divided by how many of them there turned out to be, which is not knowable
    // until the first two have finished.
    val shrink = params.spacingShrink(min(ctx.config.widthMetres, ctx.config.heightMetres))
    val placed = LinkedHashMap<MinableOre, MutableList<Site>>()
    val taken = Occupied(params.oreSeparation)

    for (mineable in MinableOre.byScarcity) {
      val kept = ArrayList<Site>()
      for (site in sitesFor(ctx, terrain, mineable, shrink)) {
        if (taken.blocks(site.position)) continue
        taken.add(site.position)
        kept.add(site)
      }
      placed[mineable] = kept
    }

    for (mineable in MinableOre.byScarcity) {
      val kept = placed.getValue(mineable)
      if (kept.size < params.minDepositsPerOre) {
        kept += guarantee(ctx, terrain, mineable, params.minDepositsPerOre - kept.size, taken)
      }
    }

    val area = ctx.config.widthMetres * ctx.config.heightMetres
    for ((mineable, sites) in placed) {
      for (candidate in sized(mineable, sites, area, voxelSize)) {
        deposits.add(marker(nextId(), candidate))
      }
    }

    // Then the surface resources, which are not competing for the same ground and skip the dispersal pass.
    for (type in ResourceType.entries) {
      if (MinableOre.of(type) != null) continue
      // Traced downstream of the lodes below, never placed.
      if (type == ResourceType.GOLD_PLACER) continue

      val suitability = terrain.suitabilityFor(type)
      val spacing = params.candidateSpacing * surfaceSpacingFactorOf(type)
      val rng = ctx.rng(CANDIDATE_STREAM, type.ordinal.toLong())

      for (site in PoissonDisk.sample(terrain.bounds, spacing, rng)) {
        val score = suitability(site)
        // Thinning a Poisson process by an acceptance probability yields a Poisson process with the
        // suitability as its intensity - which is exactly the model the architecture document asks for, and
        // it needs no rejection loop or density normalisation.
        if (score <= 0.0 || rng.nextDouble() > score) continue

        val richness = richnessAt(score, rng.nextDouble())
        val tons = SURFACE_MIN_TONS + rng.nextDouble() * (SURFACE_MAX_TONS - SURFACE_MIN_TONS) * score
        val depth = surfaceDepthOf(type, rng.nextDouble())

        deposits.add(marker(nextId(), body(type, site, score, tons, richness, depth, voxelSize)))
      }
    }

    deposits.addAll(tracePlacers(ctx, terrain, deposits.toList(), nextId, voxelSize))

    return StageResult(
      layers = listOf(terrain.valueField(deposits, params.valueRadius).toLayer(LayerId.RESOURCE_VALUE, region)),
      features = deposits
    )
  }

  /** One mineable ore's candidate sites, unsized, best ground first. */
  private fun sitesFor(
    ctx: GenContext,
    terrain: Terrain,
    mineable: MinableOre,
    shrink: Double
  ): List<Site> {
    val type = mineable.resource
    val suitability = terrain.suitabilityFor(type)
    val spacing = params.candidateSpacing * mineable.spacingFactor * shrink
    val rng = ctx.rng(CANDIDATE_STREAM, type.ordinal.toLong())
    val out = ArrayList<Site>()

    for (position in PoissonDisk.sample(terrain.bounds, spacing, rng)) {
      val score = suitability(position)
      // Thinning a Poisson process by an acceptance probability yields a Poisson process with the
      // suitability as its intensity - which is exactly the model the architecture document asks for, and
      // it needs no rejection loop or density normalisation.
      if (score <= 0.0 || rng.nextDouble() > score) continue

      // Draw order is fixed and must stay so: these come off one stream and swapping two of them reshuffles
      // every deposit of this type in the world.
      val richness = richnessAt(score, rng.nextDouble())
      val share = rng.nextDouble()
      val depth = mineable.depthAt(rng.nextDouble())

      out.add(Site(position, score, richness, depth, share))
    }

    // Best ground first, so when two sites of one ore collide the dispersal pass keeps the better of them.
    return out.sortedByDescending { it.score }
  }

  /**
   * Sites for an ore the sampler came up short on, taken from the best ground the world actually has.
   *
   * **Every world has every ore, and this is what makes that true.** The thinned Poisson process is the right
   * model and it is also a lottery: on a 128 km world mithrandium gets about fifty candidate sites, a sixth of
   * them land on ground that suits it, and the thinning keeps each with probability equal to its score - so the
   * expected count is under one and two worlds in three had none at all. That is not scarcity. A player could
   * cross the whole map and prove the ore did not exist.
   *
   * What makes the top-up honest rather than a sprinkle is that **the geology is already there**: measured on
   * the development world, seventeen per cent of land cells satisfy mithrandium's conjunction of old, hard and
   * high ground. The sampler simply missed them. So this scans for the best of those cells and puts the
   * deposit on one, which is where the causal model said it should have gone anyway.
   *
   * Only runs when an ore is short, and only then does it pay for a raster scan - so a world large enough to
   * fill its own quota never touches this.
   */
  private fun guarantee(
    ctx: GenContext,
    terrain: Terrain,
    mineable: MinableOre,
    needed: Int,
    taken: Occupied
  ): List<Site> {
    val suitability = terrain.suitabilityFor(mineable.resource)
    val rng = ctx.rng(GUARANTEE_STREAM, mineable.resource.ordinal.toLong())

    // Score cell centres, keep the ones this ore could occupy at all, best first. A cell is a kilometre, so
    // on the small worlds that actually need this it is thousands of evaluations.
    //
    // Strided rather than exhaustive, because a big world's raster is sixteen million cells and this must not
    // become a multi-second stall on the one seed whose geology happens to be awkward. Missing the single best
    // cell costs nothing: any cell scoring above zero is ground the causal model endorses, and this is a floor
    // of last resort rather than the mechanism that places ore well.
    val cells = terrain.region.width * terrain.region.height
    val stride = max(1, cells / GUARANTEE_SAMPLES)
    val ranked = ArrayList<Pair<Double, Vec2d>>()
    for (cell in 0 until cells step stride) {
      val centre = terrain.centreOf(cell)
      val score = suitability(centre)
      if (score > 0.0) ranked.add(score to centre)
    }
    ranked.sortByDescending { it.first }

    val out = ArrayList<Site>()
    for ((score, centre) in ranked) {
      if (out.size >= needed) break

      // Off the lattice, or a guaranteed deposit would sit at an exact cell centre and read as placed by a
      // grid rather than by geology.
      val position = Vec2d(
        centre.x + (rng.nextDouble() - 0.5) * terrain.metresPerCell,
        centre.y + (rng.nextDouble() - 0.5) * terrain.metresPerCell
      )
      if (taken.blocks(position)) continue
      taken.add(position)

      out.add(Site(position, score, richnessAt(score, rng.nextDouble()), mineable.depthAt(rng.nextDouble()),
        rng.nextDouble()))
    }

    return out
  }

  /**
   * Turns an ore's placed sites into deposits, splitting the world's supply of that metal between them.
   *
   * This is where [MinableOre.tonsPerThousandSqKm] earns its keep. The alternative - drawing each deposit's
   * tonnage independently - makes the world's total metal a function of how many deposits it happened to get,
   * and two mechanisms now deliberately put *more* deposits on a small world than its area would: the spacing
   * floor and the guarantee. Under an independent draw those would compound, and a 128 km world would come out
   * several times richer per square kilometre than a 512 km one for no reason a designer chose.
   *
   * The shares are uneven on purpose - a world where every iron deposit held the same tonnage would have no
   * finds in it - and they are normalised, so the unevenness costs the total nothing.
   */
  private fun sized(
    mineable: MinableOre,
    sites: List<Site>,
    areaSqMetres: Double,
    voxelSize: Double
  ): List<Candidate> {
    if (sites.isEmpty()) return emptyList()

    val total = mineable.worldTons(areaSqMetres) * params.tonnageScale
    // Squared, so most deposits are modest and a big one is a find. Floored above zero so no site is left
    // with nothing in it at all.
    val weights = sites.map { MIN_SHARE + it.share * it.share }
    val perWeight = total / weights.sum()

    return sites.mapIndexed { index, site ->
      // The floor the density model is deliberately allowed to breach. On a world small enough that its whole
      // supply of an ore divided between the sites it is guaranteed comes to a few hundred kilograms, the
      // honest answer is a deposit too small to be worth walking to - so the floor wins and the world ends up
      // slightly richer than its abundance says. That is the trade the floor exists to make.
      val tons = max(MIN_DEPOSIT_TONS, weights[index] * perWeight)
      body(mineable.resource, site.position, site.score, tons, site.richness, site.depth, voxelSize)
    }
  }

  /**
   * A candidate with its geometry solved: the tonnage, the concentration and the radius that agree.
   *
   * The tonnage is the input and the radius the output, except when the radius would be absurd - a hundred
   * tons of salt at three percent concentration is a body a quarter of a kilometre across. Then the radius
   * clamps, the concentration absorbs what it can, and the tonnage is **rewritten to what the body actually
   * holds**. A deposit that quietly claims more than it contains would break the one promise this whole
   * arrangement exists to make.
   */
  private fun body(
    type: ResourceType,
    position: Vec2d,
    score: Double,
    tons: Double,
    richness: Double,
    depth: Double,
    voxelSize: Double
  ): Candidate {
    val mean = params.grades.meanYieldKg
    val wanted = richness.coerceIn(MIN_RICHNESS, MAX_RICHNESS)
    val ideal = OreBody.radiusForTons(tons, wanted, voxelSize, mean)

    if (ideal in OreBody.MIN_RADIUS..OreBody.MAX_RADIUS) {
      return Candidate(type, position, score, tons, wanted, ideal, depth)
    }

    val radius = ideal.coerceIn(OreBody.MIN_RADIUS, OreBody.MAX_RADIUS)
    val settled = OreBody.richnessForTons(tons, radius, voxelSize, mean).coerceIn(MIN_RICHNESS, MAX_RICHNESS)
    return Candidate(type, position, score, OreBody.tonsOf(radius, settled, voxelSize, mean), settled, radius, depth)
  }

  /**
   * Where deposits have already been put, so the next one can be kept away from them.
   *
   * A uniform grid of [ResourceParams.oreSeparation]-sized cells, so asking costs a look at nine buckets
   * rather than a scan of everything placed so far. Only the *separation* is checked here, not the orebodies'
   * own extents: `ResourceParams` requires the separation to exceed the furthest two bodies could ever reject
   * each other from, so the wider rule subsumes the narrower one and the sizing pass is free to run afterwards.
   * `Invariants` re-checks both against the finished world, which is what keeps that reasoning honest.
   */
  private class Occupied(private val separation: Double) {

    private val buckets = HashMap<Long, MutableList<Vec2d>>()

    fun blocks(position: Vec2d): Boolean {
      val cx = floor(position.x / separation).toInt()
      val cy = floor(position.y / separation).toInt()

      for (dy in -1..1) {
        for (dx in -1..1) {
          val bucket = buckets[keyOf(cx + dx, cy + dy)] ?: continue
          for (other in bucket) {
            if (position.distanceTo(other) < separation) return true
          }
        }
      }
      return false
    }

    fun add(position: Vec2d) {
      val cx = floor(position.x / separation).toInt()
      val cy = floor(position.y / separation).toInt()
      buckets.getOrPut(keyOf(cx, cy)) { ArrayList() }.add(position)
    }
  }

  private fun marker(id: net.bestia.worldgen.vector.FeatureId, candidate: Candidate) = PointMarker(
    id = id,
    kind = FeatureKind.ORE_DEPOSIT,
    position = candidate.position,
    attributes = StationTable.Builder(1)
      .channel(DepositChannels.TYPE) { candidate.type.ordinal.toDouble() }
      .channel(DepositChannels.RICHNESS) { candidate.richness }
      .channel(DepositChannels.TONS) { candidate.tons }
      .channel(DepositChannels.DEPTH) { candidate.depth }
      // Solved from the tonnage rather than drawn separately, so a big deposit is a big orebody and the two
      // numbers cannot contradict each other. See `body`.
      .channel(DepositChannels.RADIUS) { candidate.radius }
      .build()
  )

  /**
   * A deposit's place in the world, before anyone knows how much metal is in it.
   *
   * Separate from [Candidate] because tonnage cannot be decided per site: it is the ore's world total split
   * between however many sites survived placement, so the split has to wait for all of them.
   *
   * @property share this site's claim on the ore's world total, before normalising against its siblings
   */
  private class Site(
    val position: Vec2d,
    val score: Double,
    val richness: Double,
    val depth: Double,
    val share: Double
  )

  /** A deposit after its geometry is solved but before it has an id. */
  private class Candidate(
    val type: ResourceType,
    val position: Vec2d,
    val score: Double,
    val tons: Double,
    val richness: Double,
    val radius: Double,
    val depth: Double
  )

  /**
   * Placer gold: the gravels downstream of a hard-rock gold deposit.
   *
   * Walks the D8 flow network from each lode and drops deposits at intervals with richness decaying by
   * distance - which is what a river actually does to eroded gold. The vector river graph is not even
   * needed for this; the raster flow directions are enough, and following them is a loop.
   */
  private fun tracePlacers(
    ctx: GenContext,
    terrain: Terrain,
    lodes: List<PointMarker>,
    nextId: () -> net.bestia.worldgen.vector.FeatureId,
    voxelSize: Double
  ): List<PointMarker> {
    val out = ArrayList<PointMarker>()
    val typeChannel = DepositChannels.TYPE
    val rng = ctx.rng(PLACER_STREAM)

    for (lode in lodes) {
      if (lode.attribute(typeChannel).toInt() != ResourceType.GOLD_LODE.ordinal) continue

      val lodeRichness = lode.attribute(DepositChannels.RICHNESS)
      var cell = terrain.cellAt(lode.position) ?: continue
      var travelled = 0.0
      var sinceLast = 0.0

      while (travelled < params.placerRange) {
        val direction = terrain.flowDirection.data[cell]
        if (direction == D8.NONE) break

        val step = D8.LENGTH[direction] * terrain.metresPerCell
        travelled += step
        sinceLast += step
        cell = terrain.step(cell, direction) ?: break

        // Placers form in gravel bars, so only where there is actually a river and it is not underwater.
        if (terrain.discharge.data[cell] < PLACER_MIN_DISCHARGE) continue
        if (!terrain.waterLevel.data[cell].isNaN()) break

        if (sinceLast < params.placerSpacing) continue
        sinceLast = 0.0

        val decay = exp(-travelled / (params.placerRange * 0.45))
        val position = terrain.centreOf(cell)
        val richness = lodeRichness * decay * (0.6 + rng.nextDouble() * 0.5)
        // A bar holds what a river could carry to it, which is a fraction of a ton, not a seam's worth.
        val tons = PLACER_TONS * decay * (0.5 + rng.nextDouble())

        out.add(
          marker(
            nextId(),
            body(
              type = ResourceType.GOLD_PLACER,
              position = position,
              score = decay,
              tons = tons,
              richness = richness,
              // In the gravels, which is why it is panned rather than mined.
              depth = rng.nextDouble() * 3.0,
              voxelSize = voxelSize
            )
          )
        )
      }
    }

    return out
  }

  /**
   * How much rarer than the baseline a surface resource's candidate sites are.
   *
   * The mineable ores answer this from [MinableOre.spacingFactor]; this covers what is left. Setting scarcity
   * per type rather than folding it into the suitability field keeps the two questions separate: suitability
   * answers "could it be here", scarcity answers "how often".
   */
  private fun surfaceSpacingFactorOf(type: ResourceType): Double = when (type) {
    ResourceType.MARBLE -> 1.7
    ResourceType.FURS -> 1.0
    ResourceType.STONE, ResourceType.CLAY, ResourceType.TIMBER, ResourceType.FISH -> 0.75

    ResourceType.COPPER, ResourceType.TIN, ResourceType.IRON, ResourceType.GOLD_LODE,
    ResourceType.GOLD_PLACER, ResourceType.SILVER, ResourceType.MITHRANDIUM, ResourceType.SALT ->
      throw IllegalArgumentException("$type is a mineable ore; its spacing is MinableOre.spacingFactor")
  }

  /** Metres below the surface. Most surface resources are at zero; marble is the one that needs digging. */
  private fun surfaceDepthOf(type: ResourceType, roll: Double): Double = when (type) {
    ResourceType.TIMBER, ResourceType.FURS, ResourceType.FISH, ResourceType.STONE -> 0.0
    ResourceType.CLAY -> roll * 6.0
    ResourceType.MARBLE -> 10.0 + roll * 140.0

    // The ore band is what the `else` here used to swallow, and it would have buried a new surface resource
    // under a hundred and fifty metres of rock for looking like marble.
    ResourceType.COPPER, ResourceType.TIN, ResourceType.IRON, ResourceType.GOLD_LODE,
    ResourceType.GOLD_PLACER, ResourceType.SILVER, ResourceType.MITHRANDIUM, ResourceType.SALT ->
      throw IllegalArgumentException("$type is a mineable ore; its depth comes from its ore body")
  }

  /**
   * Concentration in `[0,1]` for a site that scored [score], jittered by [roll].
   *
   * Good geology means rich rock, but not uniformly: two deposits on the same fault are not the same deposit.
   */
  private fun richnessAt(score: Double, roll: Double) =
    ((0.35 + score * 0.65) * (0.7 + roll * 0.6)).coerceIn(0.0, 1.0)

  companion object {
    val ID = StageId("resources")

    private const val CANDIDATE_STREAM = 1L
    private const val PLACER_STREAM = 2L
    private const val GUARANTEE_STREAM = 3L

    /** Discharge in cubic metres per second below which a stream carries no worthwhile gravel bar. */
    private const val PLACER_MIN_DISCHARGE = 2.0

    /** Tons of gold a gravel bar holds right below the lode, before the downstream decay. */
    private const val PLACER_TONS = 3.0

    /**
     * Nominal tonnage range for the surface resources, in tons.
     *
     * On the same scale as the ores so the two are comparable, but nominal: a stand of timber has no orebody
     * and nothing reads the number for it. See [DepositChannels.TONS].
     */
    private const val SURFACE_MIN_TONS = 20.0
    private const val SURFACE_MAX_TONS = 150.0

    /**
     * Bounds on concentration.
     *
     * Above [MAX_RICHNESS] a body is solid metal, which is not a thing; below [MIN_RICHNESS] it is a handful
     * of voxels scattered through a hillside, which a player would never find. Both also keep the radius
     * solve from dividing by something near zero.
     */
    private const val MIN_RICHNESS = 0.03
    private const val MAX_RICHNESS = 0.95

    /**
     * Smallest deposit worth walking to, in tons.
     *
     * The one place the density model is allowed to be overruled - see `sized`. About seven thousand ore
     * voxels, which is a mining site rather than an afternoon.
     */
    private const val MIN_DEPOSIT_TONS = 8.0

    /** Floor under a site's share of its ore's world total, so no deposit is drawn as empty. */
    private const val MIN_SHARE = 0.35

    /**
     * Cell centres the guaranteed top-up scores before picking from them.
     *
     * A bound on the worst case, not a target: a 128 km world has sixteen thousand cells and is scanned whole,
     * while a 4096 km world would be sampled one cell in forty - and a 4096 km world is never short in the
     * first place.
     */
    private const val GUARANTEE_SAMPLES = 400_000

    private fun keyOf(cx: Int, cy: Int) = (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFF_FFFFL)
  }
}

/**
 * The raster context resource placement reads: the layers, plus a couple of derived distance fields.
 *
 * A separate type because the suitability rules are the interesting part and threading a dozen grids
 * through each of them would bury them. Everything here is read once at stage entry.
 */
private class Terrain(
  val region: CellRegion,
  val metresPerCell: Double,
  val seaLevel: Double,
  val elevation: Grid,
  val hardness: Grid,
  val crustAge: Grid,
  val sediment: Grid,
  val precipitation: Grid,
  val discharge: Grid,
  val waterLevel: Grid,
  val canopy: Grid,
  val biome: IntLayer,
  val lakeId: IntLayer,
  val flowDirection: IntLayer,
  /** Metres to the nearest convergent plate boundary. */
  val toConvergent: Grid
) {

  val bounds = region.toWorld()

  fun cellAt(position: Vec2d): Int? {
    val x = (position.x / metresPerCell).toInt() - region.minX
    val y = (position.y / metresPerCell).toInt() - region.minY
    if (x < 0 || y < 0 || x >= region.width || y >= region.height) return null
    return y * region.width + x
  }

  fun centreOf(cell: Int) = Vec2d(
    (region.minX + cell % region.width + 0.5) * metresPerCell,
    (region.minY + cell / region.width + 0.5) * metresPerCell
  )

  fun step(cell: Int, direction: Int): Int? {
    val x = cell % region.width + D8.DX[direction]
    val y = cell / region.width + D8.DY[direction]
    if (x < 0 || y < 0 || x >= region.width || y >= region.height) return null
    return y * region.width + x
  }

  private fun biomeAt(cell: Int) =
    Biome.entries[biome[region.minX + cell % region.width, region.minY + cell / region.width]]

  private fun lakeAt(cell: Int) =
    lakeId[region.minX + cell % region.width, region.minY + cell / region.width]

  /**
   * Suitability in `[0,1]` for each resource type, as a function of world position.
   *
   * Each of these is a claim about geology or ecology, and each is checkable against a map: copper should
   * appear along volcanic arcs and nowhere else, mithrandium only under old high hard ground, salt on the
   * floor of terminal lakes. That falsifiability is the reason to place resources causally in the first
   * place - sprinkled resources cannot be wrong, and cannot be discovered either.
   */
  fun suitabilityFor(type: ResourceType): (Vec2d) -> Double = { position ->
    val cell = cellAt(position)
    if (cell == null) {
      0.0
    } else {
      val above = elevation.data[cell] - seaLevel
      val submerged = above <= 0.0
      val rock = hardness.data[cell]
      val age = crustAge.data[cell]
      val arc = exp(-toConvergent.data[cell] / ARC_RANGE)

      when (type) {
        // Porphyry copper forms in the intrusive roots of arc volcanoes.
        ResourceType.COPPER ->
          if (submerged) 0.0 else arc * ramp(rock, 0.4, 0.8) * 0.9

        // Tin and tungsten sit in granite plutons: old, hard, cratonised crust.
        ResourceType.TIN ->
          if (submerged) 0.0 else ramp(rock, 0.6, 0.9) * ramp(age, 0.4, 0.85) * 0.8

        // Iron by two routes, because one of them was placing almost none.
        //
        // Banded iron in ancient shields is the textbook answer and it is kept, but its elevation clause used
        // to be `600 - above`, which assumed a world whose land sat below six hundred metres. This one's
        // median land elevation is fourteen hundred, so the clause read zero nearly everywhere and a 512 km
        // world came out with a single iron deposit in it - a world with no iron in it at all, in practice.
        //
        // The second route is bog iron: iron precipitated out of groundwater into wet, sediment-laden ground.
        // It is not a consolation prize for the first. Bog and lake ore is what most medieval iron was
        // actually smelted from, it needs no shield, and it puts iron in exactly the wet lowlands where the
        // settlements are - which is the causal story worth having.
        ResourceType.IRON ->
          if (submerged) {
            0.0
          } else {
            val banded = ramp(age, 0.55, 0.95) * ramp(2500.0 - above, 0.0, 1500.0)
            val bog = ramp(precipitation.data[cell], 600.0, 1400.0) *
                ramp(sediment.data[cell], 4.0, 40.0) *
                ramp(1500.0 - above, 0.0, 900.0)
            max(banded, bog) * 0.85
          }

        // Gold with the arcs, but rarer and preferring the hardest host rock.
        ResourceType.GOLD_LODE ->
          if (submerged) 0.0 else arc * ramp(rock, 0.55, 0.9) * 0.7

        ResourceType.SILVER ->
          if (submerged) 0.0 else arc * ramp(rock, 0.45, 0.85) * 0.6

        // Placers are traced from the lodes, never placed directly.
        ResourceType.GOLD_PLACER -> 0.0

        // Mithrandium in the roots of the oldest mountains: crust that was already ancient and hard when a
        // later collision lifted it, so all three of age, hardness and height have to be high at once. That
        // conjunction is what makes it rare without a scarcity number doing the work, and it puts every
        // deposit somewhere a player has to climb to and then dig under.
        ResourceType.MITHRANDIUM ->
          if (submerged) 0.0
          else ramp(age, 0.6, 0.88) * ramp(rock, 0.58, 0.86) * ramp(above, 1000.0, 2600.0) * 0.7

        // Salt from evaporation: the bed of a terminal lake, or a dry closed basin.
        ResourceType.SALT -> when {
          lakeAt(cell) < 0 -> 1.0
          submerged -> 0.0
          else -> ramp(600.0 - above, 0.0, 600.0) * ramp(900.0 - precipitation.data[cell], 0.0, 700.0)
        }

        // Quarryable stone wants hard rock actually exposed at the surface, so thin soil and some relief.
        ResourceType.STONE ->
          if (submerged) 0.0 else ramp(rock, 0.5, 0.85) * ramp(above, 100.0, 900.0)

        // Clay in floodplains: deposited sediment on flat wet ground.
        ResourceType.CLAY ->
          if (submerged) 0.0 else ramp(sediment.data[cell], 1.0, 8.0) * 0.9

        // Marble is metamorphosed limestone, so it needs both the sediments and the heat of a collision.
        ResourceType.MARBLE ->
          if (submerged) 0.0 else arc * ramp(age, 0.3, 0.8) * ramp(1.0 - rock, 0.2, 0.6) * 0.7

        // Straight off the canopy raster, which is the one resource in this list whose suitability is
        // literally a layer. The five-way `when` over the biome this replaces graded a clearing, a young
        // stand and old growth identically as long as the classifier called them all forest, and it had an
        // `else -> 0.0` that would have gone on saying "no trees" for any biome added after it.
        ResourceType.TIMBER ->
          if (submerged) 0.0 else ramp(canopy.data[cell], 0.12, 0.55)

        ResourceType.FURS -> when (biomeAt(cell)) {
          Biome.TAIGA -> 1.0
          Biome.TUNDRA, Biome.TEMPERATE_FOREST -> 0.6
          Biome.ALPINE -> 0.4

          Biome.OCEAN, Biome.LAKE, Biome.ICE_SHEET, Biome.GLACIER, Biome.COLD_DESERT,
          Biome.TEMPERATE_RAINFOREST, Biome.GRASSLAND, Biome.STEPPE, Biome.SHRUBLAND, Biome.DESERT,
          Biome.SAVANNA, Biome.TROPICAL_SEASONAL_FOREST, Biome.TROPICAL_RAINFOREST, Biome.WETLAND,
          Biome.RIPARIAN, Biome.BEACH, Biome.BADLANDS, Biome.CLIFF -> 0.0
        }

        // Fish where there is water, best on a shallow shelf or in a lake rather than mid-ocean.
        ResourceType.FISH -> when {
          lakeAt(cell) != 0 -> 0.7
          !submerged -> 0.0
          else -> ramp(400.0 + above, 0.0, 400.0)
        }
      }.coerceIn(0.0, 1.0)
    }
  }

  /**
   * A smoothed field of nearby extractable value.
   *
   * Habitability wants "how much is worth having near here", and answering it by querying the deposit index
   * per cell would be a spatial query per cell. Stamping each deposit's contribution outwards instead is one
   * pass over the deposits, and it is the same answer.
   */
  fun valueField(deposits: List<PointMarker>, radius: Double): Grid {
    val grid = Grid(region.width, region.height)
    val typeChannel = DepositChannels.TYPE
    val radiusCells = (radius / metresPerCell).toInt() + 1

    for (deposit in deposits) {
      val type = ResourceType.entries[deposit.attribute(typeChannel).toInt()]
      val weight = type.value * deposit.attribute(DepositChannels.RICHNESS)
      if (weight <= 0.0) continue

      val centre = cellAt(deposit.position) ?: continue
      val cx = centre % region.width
      val cy = centre / region.width

      for (y in max(0, cy - radiusCells)..min(region.height - 1, cy + radiusCells)) {
        for (x in max(0, cx - radiusCells)..min(region.width - 1, cx + radiusCells)) {
          val dx = (x - cx) * metresPerCell
          val dy = (y - cy) * metresPerCell
          val distance = kotlin.math.sqrt(dx * dx + dy * dy)
          if (distance > radius) continue

          // Linear falloff rather than exponential: this is an accessibility measure, and a deposit forty
          // kilometres away is genuinely worth nothing to a village, not a little.
          grid.data[y * region.width + x] += weight * (1.0 - distance / radius)
        }
      }
    }

    for (i in grid.data.indices) {
      grid.data[i] = min(1.0, grid.data[i] / VALUE_SATURATION)
    }

    return grid
  }

  companion object {

    /** Distance over which arc-related mineralisation fades, in metres. */
    const val ARC_RANGE = 90_000.0

    /** Summed deposit weight that counts as "as good as it gets" for the value field. */
    const val VALUE_SATURATION = 2.5

    /** 0 below [low], 1 above [high], smooth between. */
    fun ramp(value: Double, low: Double, high: Double): Double {
      if (high <= low) return if (value >= high) 1.0 else 0.0
      return net.bestia.worldgen.vector.PolylineFeature.smoothstep((value - low) / (high - low))
    }

    fun read(ctx: GenContext, region: CellRegion): Terrain {
      val toConvergent = convergentDistance(ctx, region)

      return Terrain(
        region = region,
        metresPerCell = region.resolution.metresPerCell,
        seaLevel = ctx.config.seaLevel,
        elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION)),
        hardness = Grid.from(ctx.layers.float(LayerId.ROCK_HARDNESS)),
        crustAge = Grid.from(ctx.layers.float(LayerId.CRUST_AGE)),
        sediment = Grid.from(ctx.layers.float(LayerId.SEDIMENT)),
        precipitation = Grid.resampled(ctx.layers.float(LayerId.PRECIPITATION), region),
        discharge = Grid.from(ctx.layers.float(LayerId.DISCHARGE)),
        waterLevel = Grid.from(ctx.layers.float(LayerId.WATER_LEVEL)),
        canopy = Grid.from(ctx.layers.float(LayerId.CANOPY_COVER)),
        biome = ctx.layers.int(LayerId.BIOME),
        lakeId = ctx.layers.int(LayerId.LAKE_ID),
        flowDirection = ctx.layers.int(LayerId.FLOW_DIRECTION),
        toConvergent = toConvergent
      )
    }

    /**
     * Distance to the nearest convergent plate boundary, rasterised from the fault polylines.
     *
     * Read from the vector tier rather than re-derived from `plate_id`, which is the reason the tectonics
     * stage emits those polylines at all. Re-deriving would mean this stage picking its own tie-breaking for
     * where a boundary runs, and then two stages disagreeing about the location of the same fault.
     */
    private fun convergentDistance(ctx: GenContext, region: CellRegion): Grid {
      val metres = region.resolution.metresPerCell
      val onBoundary = BooleanArray(region.width * region.height)

      for (feature in ctx.features.query(region.toWorld())) {
        if (feature.kind != FeatureKind.FAULT) continue
        val fault = feature as? MarkerFeature ?: continue
        if (!isConvergent(fault)) continue

        // Walk the polyline at half-cell steps so no cell it crosses is missed.
        var s = 0.0
        while (s <= fault.centerline.length) {
          val point = fault.centerline.pointAt(s)
          val x = (point.x / metres).toInt() - region.minX
          val y = (point.y / metres).toInt() - region.minY
          if (x in 0 until region.width && y in 0 until region.height) {
            onBoundary[y * region.width + x] = true
          }
          s += metres * 0.5
        }
      }

      return DistanceTransform.euclideanMetres(region.width, region.height, metres) { x, y ->
        onBoundary[y * region.width + x]
      }.also { grid ->
        // No convergent boundary anywhere is a legitimate seed; cap so nothing downstream sees MAX_VALUE.
        val cap = max(region.width, region.height) * metres
        for (i in grid.data.indices) {
          if (grid.data[i] > cap) grid.data[i] = cap
        }
      }
    }

    private fun isConvergent(fault: MarkerFeature) =
      TectonicsStage.boundaryTypeOf(fault) == BoundaryType.CONVERGENT
  }
}

