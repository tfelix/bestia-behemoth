package net.bestia.zone.crafting.station

import org.springframework.stereotype.Component

/**
 * How far each station is with the item in progress, and why it stands still if it does.
 *
 * Runtime only: a restart starts the item in progress again, which costs nothing because its ingredients are only
 * spent when it finishes.
 */
@Component
class StationWork {

  class Progress {
    var seconds: Float = 0f

    /** How long the item in progress takes this worker; 0 until the first tick works it out. */
    var craftSeconds: Float = 0f

    var pause: StationPause = StationPause.NONE
  }

  private val byStructure = HashMap<Long, Progress>()

  fun of(structureId: Long): Progress {
    return byStructure.getOrPut(structureId) { Progress() }
  }

  fun peek(structureId: Long): Progress? {
    return byStructure[structureId]
  }

  fun forget(structureId: Long) {
    byStructure.remove(structureId)
  }
}
