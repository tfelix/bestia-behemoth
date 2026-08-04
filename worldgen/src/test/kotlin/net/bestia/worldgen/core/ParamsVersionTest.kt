package net.bestia.worldgen.core

import net.bestia.worldgen.bio.BiomeParams
import net.bestia.worldgen.bio.Biomes
import net.bestia.worldgen.civ.Culture
import net.bestia.worldgen.civ.HabitabilityParams
import net.bestia.worldgen.civ.SettlementParams
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.civ.StreetParams
import net.bestia.worldgen.civ.TownParams
import net.bestia.worldgen.climate.ClimateParams
import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.climate.WeatherParams
import net.bestia.worldgen.geo.ClosedBasinParams
import net.bestia.worldgen.geo.DetailParams
import net.bestia.worldgen.geo.DropletParams
import net.bestia.worldgen.geo.ErosionParams
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.GlacialParams
import net.bestia.worldgen.geo.TectonicsParams
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.history.HistoryParams
import net.bestia.worldgen.karst.CaveParams
import net.bestia.worldgen.mana.CorruptionParams
import net.bestia.worldgen.mana.ManaParams
import net.bestia.worldgen.voxel.VegetationParams
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.hydro.HydrologyParams
import net.bestia.worldgen.hydro.AlluviumParams
import net.bestia.worldgen.hydro.PondParams
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.EconomyParams
import net.bestia.worldgen.resource.GradeMix
import net.bestia.worldgen.resource.MinableOre
import net.bestia.worldgen.resource.ResourceParams
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.spawn.SpawnHostility
import net.bestia.worldgen.spawn.SpawnerParams
import net.bestia.worldgen.voxel.CrystalParams
import net.bestia.worldgen.voxel.StrataParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Every tunable reaches its stage's digest, and the digests do not move by accident.
 *
 * Two different jobs, and the first is the one that earns its keep. Seventeen params classes hold something
 * like two hundred and twenty fields between them and each `digest()` is written by hand, so the realistic
 * failure is not a wrong fold but a **forgotten field** - which is silent, because the digest still produces a
 * number, the number is still stable, and the tunable it does not cover simply goes on shifting terrain without
 * moving a version. [ParamsFields] reads the field names out of `toString()` and this asserts set equality
 * against what the digest folded, in both directions: a field absent from the digest fails, and so does a
 * misspelled name string in the digest.
 *
 * The second job is the pin, in the shape of `ChunkStoreTest.the block palette is pinned to the chunk engine
 * version`: a literal per class, asserted element-wise so a failure names the class rather than printing a wall
 * of hashes.
 *
 * ### When the pin fails
 *
 * You changed a tunable. That already invalidates the stage and everything downstream of it through
 * `Stage.paramsVersion`, which is the whole point - so **do not also bump the stage's `version`**. That reseeds
 * every RNG stream below the stage and changes *which* seeds show a latent bug, which is a debugging cost with
 * nothing to buy it. Re-pin the number here and say in the commit message what moved.
 *
 * `version` is for a change in what the code does; `paramsVersion` is for a change in the numbers it does it
 * with. See [Stage.version].
 */
class ParamsVersionTest {

  /**
   * Every params object in the pipeline, at its defaults.
   *
   * A new params class must be added here. Nothing forces that - which is why the list is ordered the way the
   * pipeline runs, so a missing entry is visible as a gap in a familiar sequence rather than as an absence.
   */
  private val digested: List<Params> = listOf(
    // World tier, in pipeline order.
    TectonicsParams(),
    ClimateParams(),
    ErosionParams(),
    ClosedBasinParams(),
    GlacialParams(),
    HydrologyParams(),
    PondParams(),
    AlluviumParams(),
    BiomeParams(),
    VegetationParams(),
    GradeMix(),
    ResourceParams(),
    CaveParams(),
    ManaParams(),
    HabitabilityParams(),
    SettlementParams(),
    HistoryParams(),
    CorruptionParams(),
    SpawnerParams(),
    TownParams(),
    StreetParams(),
    EconomyParams(),
    // The chunk tier, which reaches no version number at all today.
    DetailParams(),
    StrataParams(),
    DropletParams(),
    CrystalParams(),
    WeatherParams(),
    // A catalogue rather than a params object, and a tunable either way. A named preset because `name` has no
    // default - a culture without one would be a bug, and the constructor says so.
    Culture.AGRARIAN
  )

  @Test
  fun `every tunable is folded into its digest`() {
    for (params in digested) {
      val declared = ParamsFields.of(params)
      val folded = params.digest().names.toSet()

      assertEquals(
        declared,
        folded,
        "${params::class.simpleName}: the fields it declares and the fields it digests differ. " +
            "Missing from digest() ${declared - folded}, unknown to the class ${folded - declared}"
      )
    }
  }

  /**
   * The catalogues, which are tunables that happen to live in a `listOf` or an enum rather than in a params
   * class.
   *
   * Separate from [digested] because they are functions rather than [Params] instances, and there is no
   * `toString` to check them against - a completeness oracle for a hand-written list of thirty-one business
   * types would have to know the list, which is the thing it would be checking. What guards these is the pin
   * alone, so the pin is the only thing standing between a silently retuned catalogue and a shifted world.
   */
  private val catalogues: List<Pair<String, Long>> = listOf(
    "Biomes" to Biomes.catalogueDigest(),
    "Culture" to Culture.catalogueDigest(),
    "SettlementTier" to SettlementTier.catalogueDigest(),
    "BusinessCatalogue" to BusinessCatalogue.digest(),
    "Names" to Names.catalogueDigest(),
    "EventKind" to EventKind.catalogueDigest(),
    "ResourceType" to ResourceType.catalogueDigest(),
    "MinableOre" to MinableOre.catalogueDigest(),
    "SpawnHostility" to SpawnHostility.catalogueDigest()
  )

  @Test
  fun `every catalogue digest is pinned`() {
    val pinned = listOf(
      "Biomes" to -8_109_751_724_075_169_305L,
      "Culture" to -8_768_142_304_179_570_668L,
      "SettlementTier" to 6_969_176_217_374_462_215L,
      "BusinessCatalogue" to -2_565_285_581_581_779_829L,
      "Names" to -9_118_719_711_542_149_956L,
      "EventKind" to -6_113_693_738_021_732_381L,
      "ResourceType" to -992_390_895_321_877_896L,
      "MinableOre" to -963_208_236_135_789_363L,
      "SpawnHostility" to 4_718_641_820_498_173_787L
    )

    assertEquals(
      pinned.size,
      catalogues.size,
      "every catalogue needs a pinned digest. The current values are:\n" +
          catalogues.joinToString("\n") { "      \"${it.first}\" to ${it.second}L," }
    )

    assertAllPinned(pinned, catalogues)
  }

  @Test
  fun `a digest is stable across runs`() {
    // Pinned literals rather than a self-comparison, because the failure this has to catch is a digest that is
    // consistent within one process and different in the next - which is what an identity hash of an array
    // field would give, and which would make pipelineVersion move on every boot.
    val pinned = listOf(
      "TectonicsParams" to -4_155_136_708_793_604_568L,
      "ClimateParams" to -7_657_650_461_186_078_680L,
      "ErosionParams" to 454_983_928_578_723_392L,
      "ClosedBasinParams" to 5_389_246_153_518_852_453L,
      "GlacialParams" to -1_266_272_442_885_291_278L,
      "HydrologyParams" to 7_155_846_832_677_361_370L,
      "PondParams" to 8_297_690_367_138_694_458L,
      "AlluviumParams" to -2_759_034_807_589_268_237L,
      "BiomeParams" to 5_285_448_812_466_836_997L,
      "VegetationParams" to 5_359_640_679_940_278_481L,
      "GradeMix" to -7_184_838_964_596_318_845L,
      "ResourceParams" to 6_146_072_137_982_550_005L,
      "CaveParams" to -4_263_381_643_348_589_984L,
      "ManaParams" to -7_122_319_974_616_012_373L,
      "HabitabilityParams" to -8_568_146_273_010_455_127L,
      "SettlementParams" to -5_086_656_145_453_525_411L,
      "HistoryParams" to 8_735_102_529_915_467_544L,
      "CorruptionParams" to -8_182_278_140_807_375_004L,
      "SpawnerParams" to 8_240_882_274_862_173_131L,
      "TownParams" to 6_322_658_815_609_408_725L,
      "StreetParams" to -2_774_139_638_864_316_831L,
      "EconomyParams" to 6_863_789_847_631_252_411L,
      "DetailParams" to 5_837_136_561_326_550_610L,
      "StrataParams" to 5_360_263_422_566_259_310L,
      "DropletParams" to 8_150_952_456_997_203_313L,
      "CrystalParams" to 7_903_608_439_399_290_165L,
      "WeatherParams" to -969_496_167_632_375_797L,
      "Culture" to 9_142_772_940_960_129_542L
    )

    assertEquals(
      pinned.size,
      digested.size,
      "every params class needs a pinned digest. The current values are:\n" +
          digested.joinToString("\n") {
            "      \"${it::class.simpleName}\" to ${it.digest().value}L,"
          }
    )

    assertAllPinned(pinned, digested.map { (it::class.simpleName ?: "?") to it.digest().value })
  }

  @Test
  fun `no stage leaves its tunables unhashed`() {
    // `paramsVersion` is abstract with no default, so a new stage cannot forget it - but it *can* satisfy the
    // compiler with a literal zero, which is the same failure wearing a hat. Every stage in the standard
    // pipeline has tunables, so zero is always wrong here.
    val config = StandardWorld.demoConfig().copy(widthCells = 64, heightCells = 64)
    val unhashed = StandardWorld.pipeline(config).stages.filter { it.paramsVersion == 0L }.map { it.id.name }

    assertEquals(emptyList(), unhashed, "these stages return 0 from paramsVersion; fold their params instead")
  }

  @Test
  fun `retuning a stage invalidates it and everything downstream, and nothing above it`() {
    val config = StandardWorld.demoConfig().copy(widthCells = 64, heightCells = 64)
    val before = StandardWorld.pipeline(config)

    // Erosion retuned, nothing else. Tectonics and climate run above it; hydrology and biomes below.
    val after = WorldGenPipeline(
      StandardWorld.stages(config).map { stage ->
        if (stage.id != ErosionStage.ID) stage else Reversioned(stage, ErosionParams(erodibility = 0.9))
      }
    )

    assertEquals(
      before.versionOf(TectonicsStage.ID),
      after.versionOf(TectonicsStage.ID),
      "retuning erosion must leave tectonics - the expensive stage - alone"
    )
    assertEquals(before.versionOf(ClimateStage.ID), after.versionOf(ClimateStage.ID))

    assertNotEquals(before.versionOf(ErosionStage.ID), after.versionOf(ErosionStage.ID))
    assertNotEquals(
      before.versionOf(HydrologyStage.ID),
      after.versionOf(HydrologyStage.ID),
      "hydrology reads the eroded surface, so its cache key has to move too"
    )
    assertNotEquals(before.pipelineVersion, after.pipelineVersion)
  }

  @Test
  fun `retuning a stage does not move its random streams`() {
    // The property that keeps tuning usable: change one number and look at *the same* world. If the digest
    // reached GenContext.rng the world would be reseeded on every value change, and comparing two runs - the
    // only way anything in this module gets debugged - would be impossible.
    val config = StandardWorld.demoConfig().copy(widthCells = 64, heightCells = 64)
    val plain = StandardWorld.stages(config).first { it.id == ErosionStage.ID }
    val retuned = Reversioned(plain, ErosionParams(erodibility = 0.9))

    assertEquals(
      GenRng.derive(config.seed, plain.id, plain.version, 7L).nextLong(),
      GenRng.derive(config.seed, retuned.id, retuned.version, 7L).nextLong(),
      "a retuned stage must derive the same streams, or tuning reseeds the world it is trying to compare"
    )
    assertNotEquals(plain.paramsVersion, retuned.paramsVersion, "...but the version must still move")
  }

  /** A stage that is another stage with different params, so the version vector can be compared. */
  private class Reversioned(private val delegate: Stage, private val params: ErosionParams) : Stage by delegate {
    override val paramsVersion get() = params.digest().value
  }

  /**
   * Compares every pin and reports **all** the mismatches, not the first.
   *
   * Element-wise so a failure names the class rather than printing two walls of hashes, and exhaustive because
   * a change that moves one digest usually moves several - a nested params object moves its parents, and a
   * catalogue moves every stage that reads it. Failing on the first would mean one run per re-pin.
   */
  private fun assertAllPinned(pinned: List<Pair<String, Long>>, actual: List<Pair<String, Long>>) {
    val wrong = pinned.zip(actual).filter { (expected, got) -> expected != got }
    assertEquals(
      emptyList(),
      wrong.map { it.first.first },
      "these digests moved. Re-pin them - and do NOT bump the stage version to match:\n" +
          wrong.joinToString("\n") { (expected, got) ->
            if (expected.first != got.first) {
              "      the pin list is out of order: expected ${expected.first}, found ${got.first}"
            } else {
              "      \"${got.first}\" to ${got.second}L,   // was ${expected.second}"
            }
          }
    )
  }
}
