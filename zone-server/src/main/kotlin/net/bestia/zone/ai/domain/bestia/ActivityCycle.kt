package net.bestia.zone.ai.domain.bestia

/**
 * When a species is awake, and therefore when it goes looking for somewhere to lie down.
 *
 * A profile knob rather than an observation: whether it is night is a fact about the world that perception
 * reports, and whether *that* means bedtime is a fact about the creature. Keeping the two apart is what lets
 * a wolf and a deer stand on the same tile at the same hour and disagree about what to do with it.
 *
 * [CATHEMERAL] is the default precisely because it is the behaviour every archetype had before this existed:
 * it never has a resting phase, so its sleeping stays driven purely by tiredness and nothing about the
 * existing profiles changes.
 */
enum class ActivityCycle {

  /** Awake by day, asleep at night. */
  DIURNAL,

  /** Awake at night, asleep by day. */
  NOCTURNAL,

  /** No fixed rhythm — sleeps when tired, whatever the hour. */
  CATHEMERAL;

  /** Whether this is the creature's resting phase given whether it is currently [night]. */
  fun isRestingAt(night: Boolean): Boolean = when (this) {
    DIURNAL -> night
    NOCTURNAL -> !night
    CATHEMERAL -> false
  }
}
