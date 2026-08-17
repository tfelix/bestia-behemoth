package net.bestia.worldgen.spawn

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.worldgen.civ.SettlementStage
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
import net.bestia.worldgen.core.WorldWrap
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.PoissonDisk
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.mana.CorruptionStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature

/** Station channels on a [FeatureKind.BESTIA_SPAWN] marker. */
object SpawnerChannels {
  const val LEVEL_MIN = "level_min"
  const val LEVEL_MAX = "level_max"

  /** The 0..1 danger this spawner's levels came from, so a runtime can rank without re-deriving. */
  const val DANGER = "danger"

  /** How many creatures this spawner keeps alive at once. */
  const val PACK = "pack"

  /** Metres from the marker that the pack may wander. Maps onto zone-server's `Spawner.range`. */
  const val RADIUS = "radius"

  /** [Biome] ordinal here. Read with `Biome.entries[v.toInt()]`; never interpolated. */
  const val BIOME = "biome"

  const val CORRUPTION = "corruption"

  /** 1 for a boss den, 0 otherwise. */
  const val BOSS = "boss"

  /**
   * Mean annual air temperature at the den, in degrees Celsius. From `LayerId.TEMPERATURE`.
   *
   * The axis a species' temperature window is tested against. [BIOME] already carries the coarse answer -
   * nothing tropical lives on an ice sheet because no den is there - so this is what discriminates *within*
   * a biome, and `GRASSLAND` spanning cold steppe and warm savanna is the case that needs it.
   */
  const val TEMPERATURE = "temperature"

  /**
   * Summer-to-winter swing at the den, in degrees. From `LayerId.TEMPERATURE_RANGE`.
   *
   * Emitted but not yet read by anything. A channel is eight bytes per marker; a channel *added later* is a
   * second forced world regeneration, because the marker set is only rebuilt when the pipeline version
   * moves. "This never freezes" is the obvious next rule and it needs the swing beside the mean, so this is
   * the one moment where having it is free.
   */
  const val TEMPERATURE_SWING = "temperature_swing"
}

/** Tuning for [SpawnerStage]. */
data class SpawnerParams(

  /**
   * Closest two spawners may be placed, in metres.
   *
   * Used raw, not detail-scaled: how many monsters there are per square kilometre is a gameplay density, and
   * a small world wants the same one a large world has. `CaveStage.candidateSpacing` argues the same case.
   *
   * **This is the spatial-density lever, and it is quadratic** - halving it quadruples the markers the world
   * carries. [minPack]/[maxPack] cost nothing by comparison and are the right lever for "more creatures".
   * Reach for this one only when what is wanted is creatures *more often*, which is what it was reached for:
   * dens 2 500 m apart against a 220 m activation range meant a player could walk for kilometres between
   * encounters, and the wilderness measured empty.
   *
   * Calibrated from a measurement of the shipped 128 km world rather than from theory. The relation is
   * `dens/km² = K / spacing_km²` with **K ≈ 0.25**, which already folds in the ~35 % of candidates lost to
   * water, settlement clearance and [minAcceptance]. At 2 500 m that gave 656 dens on the 128 km world; at
   * 350 m it gives about **33 000**, or 2.04 dens/km², which against a mean pack of ~9.6 is ~20 creatures
   * per km² - two or three on a 352 m screen.
   *
   * Level-band distribution is unchanged by this, only scaled: `1-8 / 9-40 / 41-79 / 80-100` came out as
   * **18 / 439 / 794 / 180** per 1 432 dens. The sweep prints those four numbers per seed; a distribution
   * that collapses into one band is the failure they are there to catch.
   *
   * The cost is real and worth stating: the Poisson sampler goes from milliseconds to seconds on a 128 km
   * world, and a 512 km world produces on the order of half a million markers. See `Invariants.sweep`'s
   * memory note before running a wide sweep.
   */
  val candidateSpacing: Double = 350.0,

  /** Metres around a standing settlement inside which nothing above [homeMaxLevel] spawns. */
  val homeSafeRadius: Double = 3_000.0,

  /**
   * Hardest thing that may spawn inside [homeSafeRadius].
   *
   * Applied as a clamp on `LEVEL_MAX`, not on the centre of the range, so nothing inside the ring can roll
   * above it.
   */
  val homeMaxLevel: Int = 8,

  /** Distance to the nearest standing settlement at which its calming influence is spent, in metres. */
  val settlementSafeRange: Double = 25_000.0,

  /** Distance from any road or town at which remoteness is complete, in metres. */
  val remotenessRange: Double = 30_000.0,

  /** Elevation at which the relief term starts, in metres above sea level. */
  val mountainStart: Double = 900.0,

  /** Elevation at which the relief term is complete. The window `MITHRANDIUM`'s suitability uses. */
  val mountainFull: Double = 2_600.0,

  val weightCivilisation: Double = 1.0,
  val weightRemoteness: Double = 0.6,
  val weightRelief: Double = 0.7,
  val weightBiome: Double = 0.8,

  /** Hardest thing uncorrupted land produces. */
  val wildMaxLevel: Int = 78,

  /** Softest thing corrupted land produces. */
  val corruptedMinLevel: Int = 80,

  /** The cap. */
  val maxLevel: Int = 100,

  /** Half-width of a spawner's level range, clamped into `1..maxLevel`. */
  val levelSpread: Int = 4,

  val minPack: Int = 4,
  val maxPack: Int = 20,

  /**
   * How far from the marker a pack spreads, in metres, from gentlest to most dangerous country.
   *
   * **This is the evenness knob, and it costs nothing.** A runtime places a den's creatures inside a box of
   * this width, so the creature field is not a uniform scatter but a cluster process: knots of one den's
   * pack, with whatever lies between them. At the old 40-140 m a den was a tight huddle far smaller than a
   * screen, so at any density that produced the right *mean* the experience was still feast-or-famine -
   * around 45 % of screens holding nothing and the rest holding a clump.
   *
   * Widening the box to a fair fraction of a screen is what turns those knots into a field, and unlike
   * [candidateSpacing] it adds no markers at all. The ceiling is the runtime's, not this stage's: zone-server
   * wakes a den at `radius + activation margin` and refuses a den whose range exceeds its activation range,
   * so a radius past about 800 m has nowhere to put the margin.
   */
  val minRadius: Double = 250.0,
  val maxRadius: Double = 550.0,

  /** Chance a corrupted spawner is a boss den instead of a pack. */
  val bossChance: Double = 0.025,

  /** Metres of clearance kept from a standing settlement's own centre. */
  val settlementClearance: Double = 250.0,

  /**
   * Chance a candidate in the gentlest country still becomes a spawner.
   *
   * Not zero: a settled valley with no monsters at all is a park, and the level-one content has to live
   * somewhere. **And not low, either**, which is the part that had to be measured. At 0.25 the wilderness
   * held four times the dens of the settled country, and since the settled country is already the smaller
   * share of the map the whole world produced twelve dens below level nine - about thirty starter creatures
   * for every new master on it.
   *
   * At 0.5 remote ground is still twice as thick with dens as a river valley, which is enough for the
   * wilderness to feel like one, and the starter band roughly doubles. Density is the wrong lever for
   * difficulty anyway: what makes the far mountains dangerous is the *level*, not the count.
   *
   * A field rather than the `private const` it used to be, because a constant is in no digest: retuning it
   * moved no version number at all, so a world generated before the change and one generated after were
   * indistinguishable to the boot gate while having different wildernesses.
   */
  val minAcceptance: Double = 0.5
) : Params {

  init {
    require(candidateSpacing > 0.0) { "candidateSpacing must be positive, was $candidateSpacing" }
    require(homeSafeRadius >= 0.0) { "homeSafeRadius must not be negative, was $homeSafeRadius" }
    require(homeMaxLevel in 1..maxLevel) { "homeMaxLevel must be in 1..$maxLevel, was $homeMaxLevel" }
    require(settlementSafeRange > 0.0) { "settlementSafeRange must be positive" }
    require(remotenessRange > 0.0) { "remotenessRange must be positive" }
    require(mountainStart < mountainFull) { "mountainStart must be below mountainFull" }
    require(wildMaxLevel in 1..maxLevel) { "wildMaxLevel must be in 1..$maxLevel, was $wildMaxLevel" }
    require(corruptedMinLevel in 1..maxLevel) { "corruptedMinLevel must be in 1..$maxLevel" }
    require(corruptedMinLevel > wildMaxLevel) {
      "corruptedMinLevel $corruptedMinLevel must exceed wildMaxLevel $wildMaxLevel, or the band is not a band"
    }
    require(levelSpread >= 0) { "levelSpread must not be negative" }
    require(minPack >= 1) { "minPack must be at least 1, was $minPack" }
    require(minPack <= maxPack) { "minPack $minPack exceeds maxPack $maxPack" }
    require(minRadius > 0.0 && minRadius <= maxRadius) { "the radius range is empty" }
    require(bossChance in 0.0..1.0) { "bossChance must be a share, was $bossChance" }
    require(settlementClearance >= 0.0) { "settlementClearance must not be negative" }
    require(minAcceptance in 0.0..1.0) { "minAcceptance must be a share, was $minAcceptance" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    candidateSpacing = source.double("candidateSpacing", candidateSpacing),
    homeSafeRadius = source.double("homeSafeRadius", homeSafeRadius),
    homeMaxLevel = source.int("homeMaxLevel", homeMaxLevel),
    settlementSafeRange = source.double("settlementSafeRange", settlementSafeRange),
    remotenessRange = source.double("remotenessRange", remotenessRange),
    mountainStart = source.double("mountainStart", mountainStart),
    mountainFull = source.double("mountainFull", mountainFull),
    weightCivilisation = source.double("weightCivilisation", weightCivilisation),
    weightRemoteness = source.double("weightRemoteness", weightRemoteness),
    weightRelief = source.double("weightRelief", weightRelief),
    weightBiome = source.double("weightBiome", weightBiome),
    wildMaxLevel = source.int("wildMaxLevel", wildMaxLevel),
    corruptedMinLevel = source.int("corruptedMinLevel", corruptedMinLevel),
    maxLevel = source.int("maxLevel", maxLevel),
    levelSpread = source.int("levelSpread", levelSpread),
    minPack = source.int("minPack", minPack),
    maxPack = source.int("maxPack", maxPack),
    minRadius = source.double("minRadius", minRadius),
    maxRadius = source.double("maxRadius", maxRadius),
    bossChance = source.double("bossChance", bossChance),
    settlementClearance = source.double("settlementClearance", settlementClearance),
    minAcceptance = source.double("minAcceptance", minAcceptance)
  )

  override fun digest() = ParamsDigest()
    .put("candidateSpacing", candidateSpacing)
    .put("homeSafeRadius", homeSafeRadius)
    .put("homeMaxLevel", homeMaxLevel)
    .put("settlementSafeRange", settlementSafeRange)
    .put("remotenessRange", remotenessRange)
    .put("mountainStart", mountainStart)
    .put("mountainFull", mountainFull)
    .put("weightCivilisation", weightCivilisation)
    .put("weightRemoteness", weightRemoteness)
    .put("weightRelief", weightRelief)
    .put("weightBiome", weightBiome)
    .put("wildMaxLevel", wildMaxLevel)
    .put("corruptedMinLevel", corruptedMinLevel)
    .put("maxLevel", maxLevel)
    .put("levelSpread", levelSpread)
    .put("minPack", minPack)
    .put("maxPack", maxPack)
    .put("minRadius", minRadius)
    .put("maxRadius", maxRadius)
    .put("bossChance", bossChance)
    .put("settlementClearance", settlementClearance)
    .put("minAcceptance", minAcceptance)
}

/**
 * Where the wild things are, and how hard they are.
 *
 * Emits one [FeatureKind.BESTIA_SPAWN] marker per den. A marker is **not a monster** - it carries a pack
 * size, a level range and a radius, and a runtime turns it into creatures when a player comes near enough to
 * see them. That is what lets the world hold a populated wilderness in a few thousand markers instead of a
 * few tens of thousands of entities.
 *
 * ### Why this is a stage when `SettlementSpawnPoints` is not
 *
 * The architecture document says that one is not a stage because "it produces nothing the pipeline
 * consumes", and taken literally that criterion would make `EconomyStage` a free function too - nothing reads
 * `SETTLEMENT_ECONOMY` either. The distinction that actually holds is *what kind of thing it is*:
 * `SettlementSpawnPoints` answers a **server policy about accounts** ("which three towns may a player begin
 * near, never the capital"), with a caller-chosen limit. "This hillside is remote, corrupted and holds
 * level-ninety things" is a **fact about the world**, on the same footing as "there is copper here".
 *
 * Four things follow from being a stage, and all four are wanted:
 *
 * 1. `FeatureStore.query` answers "what is near this player" from the spatial index the vector tier already
 *    has, rather than the server building a second one over a returned list.
 * 2. The level curve reaches `paramsVersion` and therefore the boot gate. Outside the graph it would reach
 *    no version at all - exactly the hole `ChunkMaterializer.VERSION` exists to close.
 * 3. `ctx.rng` seeds from the stage id and version rather than being hand-rolled.
 * 4. `ScopedLayerStore` enforces the six layers this reads.
 *
 * ### Danger, and why corruption is a band rather than a term
 *
 * Four weighted terms make the ordinary curve - how far from people, how remote from anything at all, how
 * high and steep, and how hostile the biome. Corruption is **not** among them: corrupted land is
 * [SpawnerParams.corruptedMinLevel] to [SpawnerParams.maxLevel] whatever else is true of it. A weighted sum
 * could only express that by letting corruption dominate everywhere, which would flatten the other three
 * across the whole world to get one region right. Severity still counts *inside* the band, so a corrupted
 * mountain is worse than a corrupted plain.
 */
class SpawnerStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: SpawnerParams = SpawnerParams()
) : Stage {

  override val id = ID
  // 2: the home safety ring dropped from four settlements to three, along with
  //    SettlementSpawnPoints.MAX_HOME_CANDIDATES - the 5th-largest town is ordinary country again.
  // 3: markers carry TEMPERATURE and TEMPERATURE_SWING. A code change to what `generate` computes rather
  //    than a retune, so it belongs here and not in the params digest - a marker read for a channel it does
  //    not have throws, so a cached world tier from before this must not be treated as current.
  override val version = 3

  override val paramsVersion get() = GenRng.hash(params.digest().value, SpawnHostility.catalogueDigest())

  override val dependencies = listOf(
    GlacialStage.ID,
    HydrologyStage.ID,
    BiomeStage.ID,
    SettlementStage.ID,
    HistoryStage.ID,
    CorruptionStage.ID
  )

  override val scale = StageScale.WORLD

  override val outputs = listOf(StageOutput.Vector(FeatureKind.BESTIA_SPAWN))

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
    val waterLevel = Grid.from(ctx.layers.float(LayerId.WATER_LEVEL))
    val civDistance = Grid.from(ctx.layers.float(LayerId.CIVILISATION_DISTANCE))
    val corruption = Grid.from(ctx.layers.float(LayerId.CORRUPTION))
    val biome = ctx.layers.int(LayerId.BIOME)
    val seaLevel = ctx.config.seaLevel

    // `resampled`, not `from`: climate runs on 4 km cells on any world of 384 cells or more, while this
    // stage is at a kilometre. Indexing a climate layer with this stage's own `cell` would silently read
    // somewhere else entirely - and would look perfectly correct on a 128-cell world, where the two
    // resolutions happen to coincide. `Invariants.checkSpawnersAreWellFormed` range-checks the result for
    // exactly that reason. No `dependencies` entry is needed: ClimateStage is already in the transitive
    // closure through BiomeStage.
    val temperature = Grid.resampled(ctx.layers.float(LayerId.TEMPERATURE), region)
    val temperatureRange = Grid.resampled(ctx.layers.float(LayerId.TEMPERATURE_RANGE), region)

    val standing = standingSettlements(ctx, region)
    val homes = homeSettlements(ctx, region, standing)
    val wrap = WorldWrap(ctx.config)

    val homeRing = params.homeSafeRadius + SettlementSpawnPoints.MAX_ARRIVAL_OFFSET_METRES

    val rng = ctx.rng(CANDIDATE_STREAM)
    val nextId = FeatureIds.allocator(id)
    val spawners = ArrayList<VectorFeature>()

    for (site in PoissonDisk.sample(region.toWorld(), params.candidateSpacing, rng)) {
      val cellX = (site.x / metres).toInt() - region.minX
      val cellY = (site.y / metres).toInt() - region.minY
      if (cellX !in 0 until region.width || cellY !in 0 until region.height) continue
      val cell = cellY * region.width + cellX

      // Nothing spawns on water. Both tests, because they answer different questions: the elevation one is
      // the sea and the water level one is every lake and pond the priority flood found.
      if (elevation.data[cell] <= seaLevel) continue
      if (!waterLevel.data[cell].isNaN()) continue

      val here = Biome.entries[biome[region.minX + cellX, region.minY + cellY]]
      if (here.isWater) continue

      // Not inside a town's own houses. Everything larger than a hamlet is covered by the home ring below;
      // this is the clearance a hamlet needs and no more.
      if (standing.any { wrap.distance(site.x, site.y, it.position.x, it.position.y) < params.settlementClearance }) {
        continue
      }

      val severity = corruption.data[cell]
      val danger = dangerAt(
        civDistance = civDistance.data[cell],
        nearestSettlement = nearestStandingDistance(wrap, site, standing),
        elevation = elevation.data[cell] - seaLevel,
        biome = here
      )

      // Thinned by danger, with a floor: settled countryside must still hold *something*, or the ring around
      // every town is an empty park. A thinned Poisson process has the acceptance as its intensity, which is
      // `ResourceStage`'s own note and what makes density vary without a second sampler.
      val acceptance = params.minAcceptance + (1.0 - params.minAcceptance) * danger
      if (rng.nextDouble() > acceptance) continue

      val corrupted = severity >= CorruptionStage.CORRUPTED
      // The ring is drawn around the settlement *centre* and widened by however far the arrival point can
      // be from it, because the stage cannot see the arrival point. See MAX_ARRIVAL_OFFSET_METRES.
      val nearHome = homes.any { wrap.distance(site.x, site.y, it.x, it.y) < homeRing }

      spawners.add(
        marker(
          nextId(), site, danger, severity, corrupted, nearHome, here,
          temperature.data[cell].toDouble(), temperatureRange.data[cell].toDouble(), rng
        )
      )
    }

    return StageResult(features = spawners)
  }

  /**
   * The ordinary danger curve, `0` in a village's fields and `1` in the remotest harsh ground.
   *
   * Normalised by the sum of the weights, so retuning one term does not rescale the level curve - which it
   * would if the weights merely added up to whatever they added up to.
   */
  private fun dangerAt(
    civDistance: Double,
    nearestSettlement: Double,
    elevation: Double,
    biome: Biome
  ): Double {
    val civilisation = ramp(nearestSettlement, 0.0, params.settlementSafeRange)
    val remoteness = ramp(civDistance, 0.0, params.remotenessRange)
    val relief = ramp(elevation, params.mountainStart, params.mountainFull)
    val hostility = SpawnHostility.of(biome)

    val weights = params.weightCivilisation + params.weightRemoteness +
        params.weightRelief + params.weightBiome

    return (
        params.weightCivilisation * civilisation +
            params.weightRemoteness * remoteness +
            params.weightRelief * relief +
            params.weightBiome * hostility
        ) / weights
  }

  private fun marker(
    featureId: net.bestia.worldgen.vector.FeatureId,
    at: Vec2d,
    danger: Double,
    severity: Double,
    corrupted: Boolean,
    nearHome: Boolean,
    biome: Biome,
    temperature: Double,
    temperatureSwing: Double,
    rng: GenRng
  ): PointMarker {
    val boss = corrupted && rng.nextDouble() < params.bossChance

    val centre = when {
      boss -> params.maxLevel.toDouble()
      // Inside the band, severity and the ordinary danger share the say, so a corrupted mountain is worse
      // than a corrupted plain rather than both being pinned to the same number.
      corrupted -> lerp(
        params.corruptedMinLevel.toDouble(),
        params.maxLevel.toDouble(),
        0.5 * severity + 0.5 * danger
      )
      else -> lerp(1.0, params.wildMaxLevel.toDouble(), danger)
    }

    var levelMin = (centre - params.levelSpread).toInt().coerceIn(1, params.maxLevel)
    var levelMax = (centre + params.levelSpread).toInt().coerceIn(1, params.maxLevel)
    if (boss) {
      levelMin = params.maxLevel
      levelMax = params.maxLevel
    }

    // The home ring, applied as a clamp on the top of the range rather than on its centre - so nothing
    // inside it can roll above the cap, which is what "safe" has to mean to a level-one master.
    if (nearHome) {
      levelMax = minOf(levelMax, params.homeMaxLevel)
      levelMin = minOf(levelMin, levelMax)
    }

    val pack = if (boss) 1 else
      (params.minPack + ((params.maxPack - params.minPack) * danger).toInt()).coerceIn(params.minPack, params.maxPack)
    val radius = lerp(params.minRadius, params.maxRadius, danger)

    val attributes = StationTable.Builder(stationCount = 1)
      .channel(SpawnerChannels.LEVEL_MIN) { levelMin.toDouble() }
      .channel(SpawnerChannels.LEVEL_MAX) { levelMax.toDouble() }
      .channel(SpawnerChannels.DANGER) { danger }
      .channel(SpawnerChannels.PACK) { pack.toDouble() }
      .channel(SpawnerChannels.RADIUS) { radius }
      .channel(SpawnerChannels.BIOME) { biome.ordinal.toDouble() }
      .channel(SpawnerChannels.CORRUPTION) { severity }
      .channel(SpawnerChannels.BOSS) { if (boss) 1.0 else 0.0 }
      .channel(SpawnerChannels.TEMPERATURE) { temperature }
      .channel(SpawnerChannels.TEMPERATURE_SWING) { temperatureSwing }
      .build()

    return PointMarker(featureId, FeatureKind.BESTIA_SPAWN, at, attributes)
  }

  /** Distance to the nearest standing settlement, or the world's own extent when there is none. */
  private fun nearestStandingDistance(
    wrap: WorldWrap,
    at: Vec2d,
    standing: List<PointMarker>
  ): Double {
    var best = Double.MAX_VALUE
    for (marker in standing) {
      val d = wrap.distance(at.x, at.y, marker.position.x, marker.position.y)
      if (d < best) best = d
    }
    // A world history emptied entirely is a legitimate seed. Everywhere is then maximally remote, which is
    // the right answer rather than an error.
    return if (best == Double.MAX_VALUE) params.settlementSafeRange else best
  }

  /** The `SETTLEMENT` markers of the settlements somebody still lives in. */
  private fun standingSettlements(ctx: GenContext, region: CellRegion): List<PointMarker> {
    val alive = HashSet<Int>()
    for (feature in ctx.features.query(region.toWorld())) {
      if (feature.kind != FeatureKind.SETTLEMENT_HISTORY) continue
      val past = feature as? PointMarker ?: continue
      if (past.attribute(HistoryChannels.FOUNDED_YEAR).toInt() == 0) continue
      if (past.attribute(HistoryChannels.ABANDONED_YEAR).toInt() != 0) continue
      alive.add(past.attribute(HistoryChannels.INDEX).toInt())
    }

    return ctx.features.query(region.toWorld())
      .asSequence()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .filter { it.attribute(SettlementChannels.INDEX).toInt() in alive }
      .toList()
  }

  /**
   * Where the safety ring goes.
   *
   * **A deviation from the brief, and worth stating.** The request puts the ring around "the three spawn
   * point villages", which is `civ/SettlementSpawnPoints.choose` - and that needs an assembled
   * `GeneratedWorld` to test the ground it lands on, which does not exist while the pipeline is running. A
   * stage cannot call it.
   *
   * So the ring goes around the settlements that function *chooses from*: the largest standing ones after
   * the capital, through the shared [SettlementSpawnPoints.standingByPopulation]. One definition of "who the
   * home villages are", read by the stage and by the server, rather than two that agree until one moves.
   */
  private fun homeSettlements(
    ctx: GenContext,
    region: CellRegion,
    standing: List<PointMarker>
  ): List<Vec2d> {
    val byIndex = standing.associateBy { it.attribute(SettlementChannels.INDEX).toInt() }
    val populations = HashMap<Int, Int>()

    for (feature in ctx.features.query(region.toWorld())) {
      if (feature.kind != FeatureKind.SETTLEMENT_HISTORY) continue
      val past = feature as? PointMarker ?: continue
      val index = past.attribute(HistoryChannels.INDEX).toInt()
      if (index !in byIndex) continue
      populations[index] = past.attribute(HistoryChannels.POPULATION).toInt()
    }

    return SettlementSpawnPoints
      .homeCandidateIndices(populations)
      .mapNotNull { byIndex[it]?.position }
  }

  private fun ramp(value: Double, from: Double, to: Double): Double =
    ((value - from) / (to - from)).coerceIn(0.0, 1.0)

  private fun lerp(from: Double, to: Double, t: Double) = from + (to - from) * t

  companion object {
    val ID = StageId("spawners")

    private const val CANDIDATE_STREAM = 1L
  }
}
