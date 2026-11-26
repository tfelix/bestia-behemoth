package net.bestia.zone.doomsday

/**
 * The doom clock: a progress from 0 to 1 that runs after onset and, with nobody acting, reaches 1 exactly at the end
 * of the world's life span. Players change its pace, and great deeds push it.
 *
 * All times are days of world age, already scaled by [DoomsdayConfig.timeScale].
 */
class DoomClock(private val config: DoomsdayConfig) {

  data class State(val progress: Double, val stage: DoomStage)

  /** The clock after the world aged from [fromDay] to [toDay] at [pace], for a world that sets in at [onsetDay]. */
  fun advance(state: State, onsetDay: Double, unfoldDays: Double, fromDay: Double, toDay: Double, pace: Double): State {
    val runningDays = toDay - maxOf(fromDay, onsetDay)
    if (runningDays <= 0.0) {
      return state
    }

    return withProgress(state, state.progress + runningDays / unfoldDays * pace)
  }

  /** The clock moved by [delta] at once, for a deed. */
  fun push(state: State, delta: Double): State {
    return withProgress(state, state.progress + delta)
  }

  /** The clock set to [progress], for a GM. */
  fun set(state: State, progress: Double): State {
    return withProgress(state, progress)
  }

  /** How fast the clock runs when the world feeds the threat with [pressure] and players hold [containment] of it. */
  fun paceOf(pressure: Double, containment: Double): Double {
    return (1.0 + pressure - containment).coerceIn(config.minPace, config.maxPace)
  }

  /** How far into its predetermined life span a world of age [ageDays] is, in percent. */
  fun tPercent(ageDays: Double, lifeSpanDays: Double): Double {
    return ageDays / lifeSpanDays * 100.0
  }

  /** How the world ends now, or null while it goes on. Saved wins a tie: a world held that long has earned it. */
  fun ending(state: State, ageDays: Double, lifeSpanDays: Double): DoomEnding? {
    return when {
      tPercent(ageDays, lifeSpanDays) >= config.savedAtT -> DoomEnding.SAVED
      state.progress >= 1.0 -> DoomEnding.LOST
      else -> null
    }
  }

  private fun withProgress(state: State, progress: Double): State {
    val clamped = progress.coerceIn(0.0, 1.0)
    return State(clamped, DoomStage.of(clamped, state.stage, config.stageHysteresis))
  }
}
