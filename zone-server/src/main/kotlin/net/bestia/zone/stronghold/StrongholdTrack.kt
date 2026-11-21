package net.bestia.zone.stronghold

/** What members invest coins in at the Empyrite. See the Investment section of the Guild Strongholds page. */
enum class StrongholdTrack {
  /** The Empyrite's HP and DEF; later the watchers. */
  DEFENSE,

  /** Guild storage slots. */
  STORAGE,

  /** The daily treasure drop. */
  ECONOMY,
}

/** How far one track has come: its level, and the steps already paid towards the next one. */
data class TrackProgress(val level: Int = 0, val steps: Int = 0) {

  val isMaxed: Boolean
    get() = level >= MAX_LEVEL

  /** [count] more steps; every [STEPS_PER_LEVEL] of them make a level. Nothing is kept past the top level. */
  fun advanced(count: Int): TrackProgress {
    val total = level * STEPS_PER_LEVEL + steps + count
    if (total >= MAX_LEVEL * STEPS_PER_LEVEL) return TrackProgress(MAX_LEVEL, 0)

    return TrackProgress(total / STEPS_PER_LEVEL, total % STEPS_PER_LEVEL)
  }

  /** A capture costs the track one level. The steps towards the next one stay. */
  fun lostLevel(): TrackProgress {
    return copy(level = (level - 1).coerceAtLeast(0))
  }

  companion object {
    const val MAX_LEVEL = 10
    const val STEPS_PER_LEVEL = 5
  }
}
