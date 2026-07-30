package net.bestia.worldgen.pop

import net.bestia.worldgen.core.World
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d

/**
 * Reads the economy back out of a generated world, for tooling.
 *
 * Two different jobs, done two different ways, and the difference is worth understanding because it is the
 * difference between what the world tier *stores* and what it *decided*.
 *
 * [summaryFor] reads stored features only. Everything a household expansion needs is in the
 * `SETTLEMENT_ECONOMY` marker and the `BUSINESS` markers beside it, so this is what the zone server would do
 * at runtime when a player walks into a town: no regeneration, no pipeline, a couple of feature lookups.
 *
 * [settingFor] re-derives. The inputs to a precondition - how much cereal the catchment grows after the
 * neighbours have taken their share, which resources are in reach - are *not* stored, because nothing at
 * runtime needs them; only the conclusion is. So the "why" view rebuilds them by running the stage's own
 * derivation against the finished world, through [net.bestia.worldgen.core.WorldGenPipeline.contextFor],
 * which hands a stage exactly the scoped view the real run gave it.
 *
 * That asymmetry is the right one. A stored answer that can be recomputed is a cache that will one day be
 * stale; a recomputed answer that nothing at runtime needs costs nothing until a developer asks.
 */
object EconomyProbe {

  /**
   * Everything needed to expand a settlement's households, from stored features.
   *
   * @return null when the settlement has no economy marker - it was never founded, or is a ruin
   */
  fun summaryFor(world: World, settlement: Int): PopulationSummary? {
    val features = world.features.all()

    val economy = features
      .filter { it.kind == FeatureKind.SETTLEMENT_ECONOMY }
      .filterIsInstance<PointMarker>()
      .firstOrNull { it.attribute(EconomyChannels.INDEX).toInt() == settlement }
      ?: return null

    // The roster is recovered by counting the business markers rather than being stored as a list, because a
    // count per type is exactly what the markers already are - and a stored second copy would be one more
    // thing that could disagree with where the businesses actually stand.
    val roster = features
      .filter { it.kind == FeatureKind.BUSINESS }
      .filterIsInstance<PointMarker>()
      .filter { it.attribute(BusinessChannels.SETTLEMENT).toInt() == settlement }
      .groupingBy { it.attribute(BusinessChannels.TYPE).toInt() }
      .eachCount()
      .entries
      .sortedBy { it.key }
      .map { it.key to it.value }

    return PopulationSummary(
      settlement = settlement,
      position = economy.position,
      population = populationOf(world, settlement),
      wealth = wealthOf(world, settlement),
      householdCount = economy.attribute(EconomyChannels.HOUSEHOLD_COUNT).toInt(),
      seed = economy.attribute(EconomyChannels.HOUSEHOLD_SEED).toLong(),
      businesses = roster,
      sectors = intArrayOf(
        economy.attribute(EconomyChannels.FARMERS).toInt(),
        economy.attribute(EconomyChannels.CRAFTERS).toInt(),
        economy.attribute(EconomyChannels.TRADERS).toInt(),
        economy.attribute(EconomyChannels.SERVANTS).toInt(),
        economy.attribute(EconomyChannels.ADMINISTRATORS).toInt(),
        economy.attribute(EconomyChannels.CLERGY).toInt(),
        economy.attribute(EconomyChannels.SOLDIERS).toInt()
      )
    )
  }

  fun summaryFor(generated: GeneratedWorld, settlement: Int): PopulationSummary? =
    summaryFor(generated.world, settlement)

  /**
   * The inputs every precondition is decided against, re-derived from the finished world.
   *
   * @return null when the settlement has no economy at all
   */
  fun settingFor(generated: GeneratedWorld, settlement: Int): BusinessCatalogue.Setting? {
    val pipeline = StandardWorld.pipeline(generated.config)
    val stage = pipeline.stage(EconomyStage.ID) as? EconomyStage ?: return null
    val ctx = pipeline.contextFor(stage, generated.world)
    val region = generated.config.worldRegion.at(stage.resolution)

    val places = EconomyReader.read(ctx, region)
    return stage.evaluate(ctx, region, places)
      .firstOrNull { it.first.index == settlement }
      ?.second
      ?.setting
  }

  /** Population from the history marker, which is where the present-day number lives. */
  private fun populationOf(world: World, settlement: Int): Int =
    historyOf(world, settlement)
      ?.attribute(net.bestia.worldgen.history.HistoryChannels.POPULATION)?.toInt()
      ?: 0

  private fun wealthOf(world: World, settlement: Int): Double =
    historyOf(world, settlement)
      ?.attribute(net.bestia.worldgen.history.HistoryChannels.WEALTH)
      ?: 0.0

  private fun historyOf(world: World, settlement: Int): PointMarker? = world.features.all()
    .filter { it.kind == FeatureKind.SETTLEMENT_HISTORY }
    .filterIsInstance<PointMarker>()
    .firstOrNull {
      it.attribute(net.bestia.worldgen.history.HistoryChannels.INDEX).toInt() == settlement
    }

  /** Where a settlement's economy marker sits, for a caller that only needs the position. */
  fun positionOf(world: World, settlement: Int): Vec2d? = world.features.all()
    .filter { it.kind == FeatureKind.SETTLEMENT_ECONOMY }
    .filterIsInstance<PointMarker>()
    .firstOrNull { it.attribute(EconomyChannels.INDEX).toInt() == settlement }
    ?.position
}
