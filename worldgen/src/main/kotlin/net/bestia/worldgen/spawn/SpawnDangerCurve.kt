package net.bestia.worldgen.spawn

import net.bestia.worldgen.bio.Biome

/**
 * How dangerous a *place* is, `0` in a village's fields and `1` in the remotest harsh ground.
 *
 * Four terms - how far the nearest standing settlement is, how remote the ground is from civilisation at
 * all, how high it stands, and how hostile its biome is - normalised by the sum of their weights, so
 * retuning one does not rescale the whole level curve.
 *
 * ### Why this is not private to [SpawnerStage]
 *
 * It was, and then zone-server needed the same answer at runtime: a creature placed between the generator's
 * dens has to be as dangerous as the country it stands in, and there is no marker to read it off. The
 * alternatives were both worse than moving it here - copying the curve, which is two definitions of
 * difficulty that will drift, or reading the nearest den marker, which asks a feature index that holds well
 * over a thousand features inside a square kilometre near a city and answers about ground up to a kilometre
 * away. Same argument [SpawnHostility] makes for its own table.
 *
 * ### Two ways in, and why both
 *
 * [of] is the whole curve in one expression and is what the generator calls. [civilisation], [remoteness]
 * and [relief] expose the three *smooth* terms separately, because they vary on a kilometre scale and can be
 * rastered once at boot, while [SpawnHostility] has to be evaluated per column - the biome raster is
 * dithered per metre.
 *
 * A caller that adds the terms up itself will not always land on the last bit [of] produces. That is fine
 * for a runtime field and **not** fine inside the generator: the acceptance test that decides whether a
 * candidate becomes a den compares this against a random draw, so a one-ULP difference can add or remove a
 * den while [SpawnerStage.version] still declares a cached world tier current. Hence one expression, used by
 * the stage, rather than a sum assembled from the parts.
 */
object SpawnDangerCurve {

  fun of(
    civDistance: Double,
    nearestSettlement: Double,
    elevationAboveSea: Double,
    biome: Biome,
    params: SpawnerParams
  ): Double {
    val civilisation = civilisation(nearestSettlement, params)
    val remoteness = remoteness(civDistance, params)
    val relief = relief(elevationAboveSea, params)
    val hostility = SpawnHostility.of(biome)

    return (
        params.weightCivilisation * civilisation +
            params.weightRemoteness * remoteness +
            params.weightRelief * relief +
            params.weightBiome * hostility
        ) / weightSum(params)
  }

  /** Metres to the nearest standing settlement, as a share of the range over which its calm is spent. */
  fun civilisation(nearestSettlement: Double, params: SpawnerParams): Double {
    return ramp(nearestSettlement, 0.0, params.settlementSafeRange)
  }

  /** Distance from any road or town, as a share of the range at which remoteness is complete. */
  fun remoteness(civDistance: Double, params: SpawnerParams): Double {
    return ramp(civDistance, 0.0, params.remotenessRange)
  }

  /** Elevation above sea level, as a share of the mountain window. */
  fun relief(elevationAboveSea: Double, params: SpawnerParams): Double {
    return ramp(elevationAboveSea, params.mountainStart, params.mountainFull)
  }

  fun weightSum(params: SpawnerParams): Double {
    return params.weightCivilisation + params.weightRemoteness +
        params.weightRelief + params.weightBiome
  }

  fun ramp(value: Double, from: Double, to: Double): Double {
    return ((value - from) / (to - from)).coerceIn(0.0, 1.0)
  }
}
