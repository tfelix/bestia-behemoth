package net.bestia.zone.world

import net.bestia.zone.BestiaException

/**
 * Thrown when a world's row cannot rebuild the config the world was generated from.
 *
 * Distinct from [IncompatibleWorldException] because the remedy is: that one is a world that no longer
 * matches the build, and regenerating it is a legitimate answer. This is a `WorldConfig` field that decides
 * terrain and has no column in [PersistedWorld], so the stored row silently describes a *different* world -
 * and regenerating would write the same incomplete row again and fail identically on the next boot. It is a
 * bug in this code, not a state the operator can configure their way out of, so no policy applies to it.
 */
class IncompleteWorldRecordException(message: String) : BestiaException(CODE, message) {
  companion object {
    const val CODE = "WORLD_RECORD_INCOMPLETE"
  }
}