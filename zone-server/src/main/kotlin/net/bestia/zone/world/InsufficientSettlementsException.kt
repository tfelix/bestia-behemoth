package net.bestia.zone.world

import net.bestia.zone.BestiaException

/**
 * Thrown when a freshly created world has fewer than [WorldService.MIN_STANDING_SETTLEMENTS] standing
 * settlements and nothing can be done about it: either the seed is pinned in configuration (so
 * regenerating would produce the exact same world), or [WorldService.MAX_SETTLEMENT_RETRIES] random
 * reseeds in a row all failed the same way, which points at the configured dimensions/density rather
 * than bad luck.
 *
 * Never thrown for a world that already existed before this boot - see the three-case policy on
 * [WorldService.load].
 */
class InsufficientSettlementsException(message: String) : BestiaException(CODE, message) {
  companion object {
    const val CODE = "WORLD_TOO_FEW_SETTLEMENTS"
  }
}