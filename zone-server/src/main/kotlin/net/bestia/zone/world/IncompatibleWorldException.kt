package net.bestia.zone.world

import net.bestia.zone.BestiaException

/**
 * Thrown when the running build cannot generate the stored world's terrain.
 *
 * A hard failure on purpose. The alternative is to boot anyway and serve a world whose ground does not match
 * what its player edits were made against, which surfaces as buildings floating over new terrain and holes
 * where somebody's floor used to be - and by then the old base is gone and there is nothing to migrate from.
 */
class IncompatibleWorldException(message: String) : BestiaException(CODE, message) {
  companion object {
    const val CODE = "WORLD_PIPELINE_MISMATCH"
  }
}