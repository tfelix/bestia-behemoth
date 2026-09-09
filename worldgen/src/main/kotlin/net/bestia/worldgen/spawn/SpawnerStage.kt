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
import kotlin.math.ceil
import kotlin.math.pow

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
   * Closest two spawner *candidates* may be placed, in metres.
   *
   * Used raw, not detail-scaled: how many monsters there are per square kilometre is a gameplay density, and
   * a small world wants the same one a large world has. `CaveStage.candidateSpacing` argues the same case.
   *
   * **This is no longer the density lever.** It used to be, and being quadratic made it a bad one. Density is
   * now [starterDensity] and its three siblings - creatures per square kilometre, stated outright - and this
   * decides only how *finely* a cell's budget can be spread over the ground. The one thing it still has to
   * satisfy is supply: a kilometre cell must offer at least `density / preferredPack` candidates, which at
   * 250 m is about thirteen against a peak demand of eight. Fall below that and cells start missing their
   * budget, which `Invariants.spawnerBandDensity` is the measurement for.
   *
   * The cost is still real and still quadratic, so raise the packs before lowering this. The Poisson sampler
   * is milliseconds at kilometre spacing and seconds at this one; a 512 km world produces on the order of a
   * million candidates. See `Invariants.sweep`'s memory note before running a wide sweep.
   */
  val candidateSpacing: Double = 250.0,

  /**
   * Metres around a home settlement inside which nothing above [homeMaxLevel] spawns.
   *
   * Nine kilometres, not the three it was. The ring is the only *guaranteed* starter country on the world -
   * everywhere else depends on where the danger curve happens to land - and at three kilometres it was about
   * ninety square kilometres of a sixteen-thousand square kilometre world, which is not a region a player can
   * level in. It is also the ground [starterDensity] is aimed at, so it has to be big enough to aim at.
   */
  val homeSafeRadius: Double = 9_000.0,

  /**
   * Hardest thing that may spawn inside [homeSafeRadius].
   *
   * Applied as a clamp on `LEVEL_MAX`, not on the centre of the range, so nothing inside the ring can roll
   * above it. Doubles as the top of the starter band - see [bandOf].
   */
  val homeMaxLevel: Int = 8,

  /**
   * Top of the second level band, the one between the starter ring and the wilderness proper.
   *
   * The only band boundary not already implied by another tunable: the starter band ends at [homeMaxLevel]
   * and the endgame begins at [corruptedMinLevel]. A param rather than a constant because it now decides a
   * *density*, and a constant that decides the world reaches no digest - which is the hole `minAcceptance`
   * was promoted out of a `private const` to close, and it is not worth reopening.
   */
  val midMaxLevel: Int = 40,

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

  /** Softest thing corrupted land produces. Doubles as the bottom of the endgame band - see [bandOf]. */
  val corruptedMinLevel: Int = 80,

  /** The cap. */
  val maxLevel: Int = 100,

  /** Half-width of a spawner's level range, clamped into `1..maxLevel`. */
  val levelSpread: Int = 4,

  /**
   * Exponent on `danger` before the ordinary level lerp, so `level = lerp(1, wildMaxLevel, danger^n)`.
   *
   * **Above 1 buys low-level country.** At 1.0 - a straight lerp - level eight needs `danger <= 0.09`, and
   * the biome hostility term alone contributes `0.8 * hostility / 3.1`, which is 0.039 in grassland before
   * any of the other three terms have said anything. The measured result was **eighteen dens out of fourteen
   * hundred in the starter band**, with 55 % of the world sitting in 41-79. At 1.6, `danger 0.35` maps to
   * level 16 rather than 28, and the distribution moves down into the bands players spend their time in.
   *
   * It does not touch the corrupted band, which is a band rather than a point on this curve, nor the home
   * ring, which is a clamp applied afterwards.
   */
  val levelCurve: Double = 1.6,

  /**
   * Creatures per square kilometre in the starter band, `1..homeMaxLevel`.
   *
   * ### Why density is stated here instead of emerging
   *
   * It used to emerge, from `candidateSpacing` x K x acceptance x pack x the server's two multipliers - six
   * multiplicative factors, no number anybody could point at, and three of them pointing the *same way*. The
   * acceptance roll gave dangerous ground twice the dens, pack gave it five times the creatures and radius
   * twice the spread, so the ground a level-one master walks out onto measured about **ten creatures per
   * square kilometre** - roughly one on a 352 m screen - while remote mountains reached eighty-five. The
   * emptiest country on the world was the starter country, and no single knob could say otherwise.
   *
   * So the budget is now a plain statement, per band, in the unit the question gets asked in. Two hundred is
   * about twenty-five creatures on a screen: a field to level in rather than a scavenger hunt.
   *
   * ### How a cell spends it
   *
   * `SpawnerStage.generate` divides the budget by [starterPack] to get a den count, takes that many of the
   * kilometre cell's Poisson candidates, then divides the budget *back* by however many dens it actually got
   * - so pack size absorbs a cell that could not supply enough candidates, and the creature count is met even
   * where the den count is not. [packCeiling] bounds that absorption.
   */
  val starterDensity: Double = 200.0,

  /** Creatures per square kilometre in `homeMaxLevel+1..midMaxLevel`. See [starterDensity]. */
  val midDensity: Double = 120.0,

  /** Creatures per square kilometre in `midMaxLevel+1..corruptedMinLevel-1`. See [starterDensity]. */
  val wildDensity: Double = 60.0,

  /**
   * Creatures per square kilometre in the endgame band, `corruptedMinLevel..maxLevel`.
   *
   * An eighth of the starter figure, deliberately. A level-ninety pack is a fight rather than scenery, and the
   * density that makes starter country feel alive would make the far mountains impassable. Density is still
   * the wrong lever for *difficulty* - that is the level - but it is the right one for how often a fight you
   * have to take seriously is forced on you.
   */
  val endgameDensity: Double = 25.0,

  /**
   * Creatures per den the starter band aims for, before a cell's candidate supply has its say.
   *
   * The split between "how many creatures" and "how many knots of them", and the two are independent: the
   * band's density fixes the first, this fixes the second. Large in the starter band because a wide loose
   * field of weak things is what low-level country should read as, and small in the endgame because a knot
   * of six elites is the shape of a fight.
   *
   * It also sets the den count, and so the marker count the world carries: `density / this` per square km.
   */
  val starterPack: Int = 25,

  /** Creatures per den in `homeMaxLevel+1..midMaxLevel`. See [starterPack]. */
  val midPack: Int = 20,

  /** Creatures per den in `midMaxLevel+1..corruptedMinLevel-1`. See [starterPack]. */
  val wildPack: Int = 12,

  /** Creatures per den in the endgame band. See [starterPack]. */
  val endgamePack: Int = 6,

  /**
   * Absolute ceiling on a den's pack, whatever a cell's budget divided by its candidate supply comes to.
   *
   * Matches zone-server's `WildSpawnConfig.maxPack`, and must not exceed it: the server clamps to its own
   * ceiling and warns, so a larger number here would quietly mean a budget the runtime refuses to spend. A
   * cell that hits this could not reach its creature target, and `Invariants.spawnerBandDensity` is where
   * that shows up - as a band measuring below the number it was given.
   */
  val packCeiling: Int = 40,

  /**
   * How far from the marker a pack spreads, in metres, in the gentlest and the most dangerous country.
   *
   * **Down from 250-550, and the reduction is the point.** A runtime wakes a den at `radius + activation
   * margin` and stocks the whole pack, so the creatures a player is *charged* for go as the square of this
   * while the ones they can *see* go as the screen. At 550 m the ratio was thirteen stocked for one visible.
   * Tightening it brings that near four, which is what makes the density above affordable at all.
   *
   * The old wide box was buying evenness - at 40-140 m a den was a tight huddle far smaller than a screen and
   * 45 % of screens held nothing. That job is now done by there being six to eight dens in every square
   * kilometre instead of two, which is the cheaper way to buy it: radius costs entities quadratically while
   * den count costs markers linearly.
   *
   * The hard ceiling is the runtime's, not this stage's: zone-server refuses a den whose range exceeds its
   * activation range, so a radius past about 800 m has nowhere to put the margin.
   */
  val gentleRadius: Double = 220.0,
  val dangerousRadius: Double = 300.0,

  /** Chance a corrupted spawner is a boss den instead of a pack. */
  val bossChance: Double = 0.025,

  /** Metres of clearance kept from a standing settlement's own centre. */
  val settlementClearance: Double = 250.0
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
    // The four bands must be four bands, or a density is stated for ground no den can land in.
    require(midMaxLevel > homeMaxLevel) {
      "midMaxLevel $midMaxLevel must exceed homeMaxLevel $homeMaxLevel, or the starter band swallows the next"
    }
    require(midMaxLevel < corruptedMinLevel) {
      "midMaxLevel $midMaxLevel must be below corruptedMinLevel $corruptedMinLevel, or the wild band is empty"
    }
    require(levelSpread >= 0) { "levelSpread must not be negative" }
    require(levelCurve > 0.0) { "levelCurve must be positive, was $levelCurve" }
    require(starterDensity > 0.0 && midDensity > 0.0 && wildDensity > 0.0 && endgameDensity > 0.0) {
      "every band's creature density must be positive, were " +
          "$starterDensity/$midDensity/$wildDensity/$endgameDensity"
    }
    require(packCeiling >= 1) { "packCeiling must be at least 1, was $packCeiling" }
    require(
      starterPack in 1..packCeiling && midPack in 1..packCeiling &&
          wildPack in 1..packCeiling && endgamePack in 1..packCeiling
    ) {
      "every band's preferred pack must be in 1..$packCeiling, were " +
          "$starterPack/$midPack/$wildPack/$endgamePack"
    }
    require(gentleRadius > 0.0 && gentleRadius <= dangerousRadius) { "the radius range is empty" }
    require(bossChance in 0.0..1.0) { "bossChance must be a share, was $bossChance" }
    require(settlementClearance >= 0.0) { "settlementClearance must not be negative" }
  }

  /**
   * Which of the four bands a den's `LEVEL_MAX` falls in: 0 starter, 1 mid, 2 wild, 3 endgame.
   *
   * The same `1-8 / 9-40 / 41-79 / 80-100` grouping `Invariants.spawnerCensus` prints and zone-server's
   * `WildSpawnConfig.Band` keys on, expressed in terms of the tunables that already decide those edges
   * rather than as a fourth copy of the numbers.
   */
  fun bandOf(levelMax: Int): Int = when {
    levelMax <= homeMaxLevel -> 0
    levelMax <= midMaxLevel -> 1
    levelMax < corruptedMinLevel -> 2
    else -> 3
  }

  /** Creatures per square kilometre for a [bandOf] index. */
  fun densityOf(band: Int): Double = when (band) {
    0 -> starterDensity
    1 -> midDensity
    2 -> wildDensity
    else -> endgameDensity
  }

  /** Creatures per den for a [bandOf] index, before candidate supply has its say. */
  fun preferredPackOf(band: Int): Int = when (band) {
    0 -> starterPack
    1 -> midPack
    2 -> wildPack
    else -> endgamePack
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    candidateSpacing = source.double("candidateSpacing", candidateSpacing),
    homeSafeRadius = source.double("homeSafeRadius", homeSafeRadius),
    homeMaxLevel = source.int("homeMaxLevel", homeMaxLevel),
    midMaxLevel = source.int("midMaxLevel", midMaxLevel),
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
    levelCurve = source.double("levelCurve", levelCurve),
    starterDensity = source.double("starterDensity", starterDensity),
    midDensity = source.double("midDensity", midDensity),
    wildDensity = source.double("wildDensity", wildDensity),
    endgameDensity = source.double("endgameDensity", endgameDensity),
    starterPack = source.int("starterPack", starterPack),
    midPack = source.int("midPack", midPack),
    wildPack = source.int("wildPack", wildPack),
    endgamePack = source.int("endgamePack", endgamePack),
    packCeiling = source.int("packCeiling", packCeiling),
    gentleRadius = source.double("gentleRadius", gentleRadius),
    dangerousRadius = source.double("dangerousRadius", dangerousRadius),
    bossChance = source.double("bossChance", bossChance),
    settlementClearance = source.double("settlementClearance", settlementClearance)
  )

  override fun digest() = ParamsDigest()
    .put("candidateSpacing", candidateSpacing)
    .put("homeSafeRadius", homeSafeRadius)
    .put("homeMaxLevel", homeMaxLevel)
    .put("midMaxLevel", midMaxLevel)
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
    .put("levelCurve", levelCurve)
    .put("starterDensity", starterDensity)
    .put("midDensity", midDensity)
    .put("wildDensity", wildDensity)
    .put("endgameDensity", endgameDensity)
    .put("starterPack", starterPack)
    .put("midPack", midPack)
    .put("wildPack", wildPack)
    .put("endgamePack", endgamePack)
    .put("packCeiling", packCeiling)
    .put("gentleRadius", gentleRadius)
    .put("dangerousRadius", dangerousRadius)
    .put("bossChance", bossChance)
    .put("settlementClearance", settlementClearance)
}

/**
 * Where the wild things are, and how hard they are.
 *
 * Emits one [FeatureKind.BESTIA_SPAWN] marker per den. A marker is **not a monster** - it carries a pack
 * size, a level range and a radius, and a runtime turns it into creatures when a player comes near enough to
 * see them. That is what lets the world hold a populated wilderness in a few tens of thousands of markers
 * instead of a million entities.
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
 *
 * ### The per-cell creature budget
 *
 * Danger decides *difficulty*. It no longer decides *density*, and the two being welded together is what had
 * to be taken apart: an acceptance roll thinned gentle ground by half, pack size scaled from four to twenty
 * with danger and the spread from 250 m to 550 m, all three pointing the same way, so the starter country
 * around the home towns came out the emptiest ground on the world at about ten creatures per square
 * kilometre. `SpawnerParams.starterDensity` states the whole of that argument now.
 *
 * So placement runs in two passes. The first is unchanged - Poisson-disk candidates, rejected for sea, lake,
 * water biome and a settlement's own houses. The second walks the **kilometre cells** the base raster already
 * has, and for each one:
 *
 *  - reads the level a den here would centre on, and from it the band;
 *  - multiplies the band's creatures/km2 by the cell's area to get a creature budget;
 *  - divides by the band's preferred pack to get a den count, capped by how many candidates the cell has;
 *  - divides the budget *back* by the dens it got, so pack size absorbs a short cell;
 *  - emits markers for the **first n candidates** in that cell.
 *
 * First-n is the load-bearing detail. Poisson order is already blue noise and seeded from `ctx.rng`, so a
 * prefix of it is a spacing-preserving subset - it keeps the minimum-distance property that makes the field
 * even, which is exactly what per-cell *resampling* would throw away by clumping at the cell seams. And a
 * per-cell quota has near-zero variance where the old independent coin flip had Poisson variance, which is
 * the other half of why the field read as irregular.
 *
 * What this trades away, stated plainly: the runtime can no longer *thicken* a band. Every band is emitted at
 * its own density now, so `WildSpawnConfig.Band.denShare` can only thin further and wanting more of something
 * is a params edit and a regenerated world. That was accepted deliberately - the alternative is emitting
 * every band at the thickest band's density and throwing seven eighths of the endgame markers away.
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
  // 4: density became a per-cell creature budget instead of a per-candidate acceptance roll. `generate` now
  //    allocates feature ids in cell order rather than in Poisson order, so every den is renamed even where
  //    the arithmetic would have agreed - and `DenIdentity` hashes that id, so a stored pack must not be
  //    handed back to a den that is no longer the same den.
  override val version = 4

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

    // Pass one: candidates that survived the ground tests, bucketed by the kilometre cell they stand in.
    // Poisson order is preserved inside a bucket, and pass two takes a prefix of it - see the class KDoc.
    val byCell = HashMap<Int, MutableList<Vec2d>>()

    for (site in PoissonDisk.sample(region.toWorld(), params.candidateSpacing, rng)) {
      val cellX = (site.x / metres).toInt() - region.minX
      val cellY = (site.y / metres).toInt() - region.minY
      if (cellX !in 0 until region.width || cellY !in 0 until region.height) continue
      val cell = cellY * region.width + cellX

      // Nothing spawns on water. Both tests, because they answer different questions: the elevation one is
      // the sea and the water level one is every lake and pond the priority flood found.
      if (elevation.data[cell] <= seaLevel) continue
      if (!waterLevel.data[cell].isNaN()) continue
      if (Biome.entries[biome[region.minX + cellX, region.minY + cellY]].isWater) continue

      // Not inside a town's own houses. Everything larger than a hamlet is covered by the home ring below;
      // this is the clearance a hamlet needs and no more.
      if (standing.any { wrap.distance(site.x, site.y, it.position.x, it.position.y) < params.settlementClearance }) {
        continue
      }

      byCell.getOrPut(cell) { ArrayList() }.add(site)
    }

    val cellKm2 = (metres / 1_000.0) * (metres / 1_000.0)
    val spawners = ArrayList<VectorFeature>()

    // Pass two, in **sorted** cell order. A feature id has to be a function of the world and not of HashMap
    // iteration order: `DenIdentity` hashes it, so an unstable order would rename every den on a restart and
    // throw away every stored pack.
    for (cell in byCell.keys.sorted()) {
      val candidates = byCell.getValue(cell)

      val cellX = cell % region.width
      val cellY = cell / region.width
      val here = Biome.entries[biome[region.minX + cellX, region.minY + cellY]]

      // The cell's own centre, in world metres. Every raster this stage reads is per-cell already, so a
      // budget is a cell-level decision and the centre is the honest place to ask from. It also costs one
      // settlement sweep per cell instead of one per candidate, which at this spacing is an order of
      // magnitude fewer.
      val centre = Vec2d((region.minX + cellX + 0.5) * metres, (region.minY + cellY + 0.5) * metres)

      val severity = corruption.data[cell]
      val corrupted = severity >= CorruptionStage.CORRUPTED
      // The ring is drawn around the settlement *centre* and widened by however far the arrival point can be
      // from it, because the stage cannot see the arrival point. See MAX_ARRIVAL_OFFSET_METRES - it is over a
      // kilometre, which is also the slack that lets this be asked at the cell centre rather than per site.
      val nearHome = homes.any { wrap.distance(centre.x, centre.y, it.x, it.y) < homeRing }

      val danger = dangerAt(
        civDistance = civDistance.data[cell],
        nearestSettlement = nearestStandingDistance(wrap, centre, standing),
        elevation = elevation.data[cell] - seaLevel,
        biome = here
      )

      // The band comes from the level an ordinary den here would reach, with no per-marker jitter and no
      // boss roll - a budget cannot depend on a coin flip that has not happened yet.
      val band = params.bandOf(levelsAt(danger, severity, corrupted, nearHome, boss = false).max)

      val budget = params.densityOf(band) * cellKm2
      val wanted = ceil(budget / params.preferredPackOf(band)).toInt().coerceAtLeast(1)
      val dens = minOf(wanted, candidates.size)
      // Divided back by the dens actually available, so pack absorbs a cell the candidate field came up
      // short on. `packCeiling` is where absorption stops and the cell simply misses its budget.
      val pack = ceil(budget / dens).toInt().coerceIn(1, params.packCeiling)
      val radius = lerp(params.gentleRadius, params.dangerousRadius, danger)

      for (i in 0 until dens) {
        spawners.add(
          marker(
            nextId(), candidates[i], danger, severity, corrupted, nearHome, here, pack, radius,
            temperature.data[cell].toDouble(), temperatureRange.data[cell].toDouble(), rng
          )
        )
      }
    }

    return StageResult(features = spawners)
  }

  /**
   * The ordinary danger curve, `0` in a village's fields and `1` in the remotest harsh ground.
   *
   * The curve itself lives in [SpawnDangerCurve] because zone-server needs the same answer for ground
   * between the dens; that file explains why it is shared rather than copied.
   */
  private fun dangerAt(
    civDistance: Double,
    nearestSettlement: Double,
    elevation: Double,
    biome: Biome
  ): Double {
    return SpawnDangerCurve.of(
      civDistance = civDistance,
      nearestSettlement = nearestSettlement,
      elevationAboveSea = elevation,
      biome = biome,
      params = params
    )
  }

  /** The level range a den in some ground gets. */
  private class Levels(val min: Int, val max: Int)

  /**
   * The `LEVEL_MIN`/`LEVEL_MAX` pair for given ground, with nothing random about it but the [boss] flag.
   *
   * Pulled out of [marker] because the per-cell creature budget is keyed on the *band*, so the band has to be
   * known before any marker in the cell is built - and it must be the same function, or a cell could be given
   * one band's density and then filled with the next band's dens.
   */
  private fun levelsAt(
    danger: Double,
    severity: Double,
    corrupted: Boolean,
    nearHome: Boolean,
    boss: Boolean
  ): Levels {
    if (boss) return Levels(params.maxLevel, params.maxLevel)

    val centre = when {
      // Inside the band, severity and the ordinary danger share the say, so a corrupted mountain is worse
      // than a corrupted plain rather than both being pinned to the same number.
      corrupted -> lerp(
        params.corruptedMinLevel.toDouble(),
        params.maxLevel.toDouble(),
        0.5 * severity + 0.5 * danger
      )
      // `levelCurve` bends this so low-level country is a region rather than a rounding error.
      else -> lerp(1.0, params.wildMaxLevel.toDouble(), danger.pow(params.levelCurve))
    }

    val levelMin = (centre - params.levelSpread).toInt().coerceIn(1, params.maxLevel)
    val levelMax = (centre + params.levelSpread).toInt().coerceIn(1, params.maxLevel)

    // The home ring, applied as a clamp on the top of the range rather than on its centre - so nothing
    // inside it can roll above the cap, which is what "safe" has to mean to a level-one master.
    if (nearHome) {
      val capped = minOf(levelMax, params.homeMaxLevel)
      return Levels(minOf(levelMin, capped), capped)
    }

    return Levels(levelMin, levelMax)
  }

  private fun marker(
    featureId: net.bestia.worldgen.vector.FeatureId,
    at: Vec2d,
    danger: Double,
    severity: Double,
    corrupted: Boolean,
    nearHome: Boolean,
    biome: Biome,
    pack: Int,
    radius: Double,
    temperature: Double,
    temperatureSwing: Double,
    rng: GenRng
  ): PointMarker {
    val boss = corrupted && rng.nextDouble() < params.bossChance
    val levels = levelsAt(danger, severity, corrupted, nearHome, boss)

    val attributes = StationTable.Builder(stationCount = 1)
      .channel(SpawnerChannels.LEVEL_MIN) { levels.min.toDouble() }
      .channel(SpawnerChannels.LEVEL_MAX) { levels.max.toDouble() }
      .channel(SpawnerChannels.DANGER) { danger }
      // A boss is one creature, whatever the cell's budget said.
      .channel(SpawnerChannels.PACK) { (if (boss) 1 else pack).toDouble() }
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

  private fun lerp(from: Double, to: Double, t: Double) = from + (to - from) * t

  companion object {
    val ID = StageId("spawners")

    private const val CANDIDATE_STREAM = 1L
  }
}
