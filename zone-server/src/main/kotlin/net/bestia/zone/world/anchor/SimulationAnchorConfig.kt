package net.bestia.zone.world.anchor

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property maxHoldingAnchors anchors beyond this keep no ground resident, by [AnchorPriority]. A safety cap:
 *   an anchor away from others holds a full view volume, about 180 chunks or 2 MB of walk data.
 * @property slabComputationsPerTick new slab ranges computed per tick for all anchors together, the same
 *   cost `chunk-stream.slab-computations-per-tick` bounds for clients.
 */
@ConfigurationProperties(prefix = "simulation-anchors")
data class SimulationAnchorConfig(
  val maxHoldingAnchors: Int = 512,
  val slabComputationsPerTick: Int = 4,
) {
  init {
    require(maxHoldingAnchors >= 0) { "The anchor cap cannot be negative" }
    require(slabComputationsPerTick > 0) { "A slab budget of zero would never hold any ground" }
  }
}
