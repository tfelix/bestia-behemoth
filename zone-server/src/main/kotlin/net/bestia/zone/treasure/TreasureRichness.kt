package net.bestia.zone.treasure

import net.bestia.worldgen.treasure.TreasureCache
import net.bestia.worldgen.treasure.TreasureTier
import net.bestia.zone.spawn.ecs.ambient.AmbientSiteResolver
import org.springframework.stereotype.Service

/**
 * How rich a cache is. Remote, dangerous country holds richer caches, by the same danger that sets the bestias'
 * levels; a history site can lift a cache above its country.
 */
@Service
class TreasureRichness(
  private val danger: AmbientSiteResolver,
) {

  fun of(cache: TreasureCache): TreasureTier {
    val tiers = TreasureTier.entries
    val index = (danger.dangerAt(cache.x, cache.y) * tiers.size).toInt()
    return maxOf(tiers[index.coerceIn(0, tiers.lastIndex)], cache.minTier)
  }
}
