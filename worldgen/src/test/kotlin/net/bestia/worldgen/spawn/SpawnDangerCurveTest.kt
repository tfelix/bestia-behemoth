package net.bestia.worldgen.spawn

import net.bestia.worldgen.bio.Biome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * That lifting the danger curve out of [SpawnerStage] changed nothing about the worlds already generated.
 *
 * The stakes are the reason this is pinned rather than eyeballed. [SpawnerStage.version] and its
 * `paramsVersion` are what the boot gate compares a stored world against, so an extraction that shifted the
 * curve by a single bit while leaving both alone would let a server keep a chunk tier that no longer matches
 * the code that made it - and the symptom would be dens quietly appearing or vanishing, not a failure.
 */
class SpawnDangerCurveTest {

  private val params = SpawnerParams()

  /**
   * The literals are the point: they are the values `SpawnerStage` computed before the curve moved. Reading
   * them from [SpawnDangerCurve] instead would make this test pass by construction.
   */
  @Test
  fun `the curve matches the arithmetic SpawnerStage used to do inline`() {
    val cases = listOf(
      Triple(0.0, 0.0, 0.0) to Biome.GRASSLAND,
      Triple(30_000.0, 25_000.0, 2_600.0) to Biome.VOLCANIC_FIELD,
      Triple(12_000.0, 4_000.0, 1_400.0) to Biome.TEMPERATE_FOREST,
      Triple(0.0, 25_000.0, 0.0) to Biome.DESERT,
      Triple(30_000.0, 0.0, 900.0) to Biome.BEACH,
      Triple(45_000.0, 90_000.0, 9_000.0) to Biome.SWAMP
    )

    for ((inputs, biome) in cases) {
      val (civDistance, nearestSettlement, elevation) = inputs

      val civilisation = ((nearestSettlement - 0.0) / (params.settlementSafeRange - 0.0)).coerceIn(0.0, 1.0)
      val remoteness = ((civDistance - 0.0) / (params.remotenessRange - 0.0)).coerceIn(0.0, 1.0)
      val relief = ((elevation - params.mountainStart) / (params.mountainFull - params.mountainStart))
        .coerceIn(0.0, 1.0)
      val hostility = SpawnHostility.of(biome)
      val weights = params.weightCivilisation + params.weightRemoteness +
          params.weightRelief + params.weightBiome
      val expected = (
          params.weightCivilisation * civilisation +
              params.weightRemoteness * remoteness +
              params.weightRelief * relief +
              params.weightBiome * hostility
          ) / weights

      val actual = SpawnDangerCurve.of(civDistance, nearestSettlement, elevation, biome, params)

      // Exact, not a tolerance: the acceptance test downstream compares this against a random draw, so the
      // last bit decides whether a den exists.
      assertEquals(expected, actual, 0.0, "danger at $inputs in $biome")
    }
  }

  /**
   * A caller that adds the smooth terms and the hostility term separately - which a runtime raster must, to
   * cache the smooth part - lands on the same curve.
   *
   * A tolerance here rather than exactness, deliberately: the split changes the order of the divisions, so
   * the last bit may differ. That is why [SpawnDangerCurve.of] and not a reassembled sum is what the
   * generator calls.
   */
  @Test
  fun `the smooth terms plus hostility reproduce the whole curve`() {
    for (biome in Biome.entries) {
      val civDistance = 7_000.0
      val nearestSettlement = 9_000.0
      val elevation = 1_100.0

      val weights = SpawnDangerCurve.weightSum(params)
      val smooth = (
          params.weightCivilisation * SpawnDangerCurve.civilisation(nearestSettlement, params) +
              params.weightRemoteness * SpawnDangerCurve.remoteness(civDistance, params) +
              params.weightRelief * SpawnDangerCurve.relief(elevation, params)
          ) / weights
      val hostility = params.weightBiome * SpawnHostility.of(biome) / weights

      val whole = SpawnDangerCurve.of(civDistance, nearestSettlement, elevation, biome, params)

      assertEquals(whole, smooth + hostility, 1e-12, "reassembled danger in $biome")
    }
  }

  @Test
  fun `the curve stays within zero and one for inputs far outside every window`() {
    for (biome in Biome.entries) {
      val danger = SpawnDangerCurve.of(-5_000.0, -5_000.0, -5_000.0, biome, params)
      assertTrue(danger in 0.0..1.0, "danger $danger in $biome")

      val extreme = SpawnDangerCurve.of(1e9, 1e9, 1e9, biome, params)
      assertTrue(extreme in 0.0..1.0, "danger $extreme in $biome")
    }
  }

  /**
   * The two numbers the boot gate compares, pinned to literals.
   *
   * If a change genuinely alters what the stage produces, both this test and the constant move together and
   * the gate regenerates the world - which is the point. Editing the literal to make the build pass without
   * that being the intent is the mistake this exists to make loud.
   *
   * Moved 4 -> 5 by the home ring coming to cap the boss roll: a stored world can hold a level-100 den
   * inside a starter town's ring, so it has to be regenerated. Extracting the curve into [SpawnDangerCurve]
   * is the pure refactor the tests above measure, and moves neither number.
   */
  @Test
  fun `SpawnerStage declares version five and an unchanged params digest`() {
    val stage = SpawnerStage()

    assertEquals(5, stage.version)
    assertEquals(
      net.bestia.worldgen.core.GenRng.hash(
        SpawnerParams().digest().value,
        SpawnHostility.catalogueDigest()
      ),
      stage.paramsVersion
    )
  }
}
