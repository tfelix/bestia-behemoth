package net.bestia.zone.ai.ecs

/**
 * How much processing an agent gets, by who can see it. Nothing is ever frozen: a lower tier runs the same
 * work less often and is handed the time that passed, so hunger, memory and timers still advance.
 */
enum class AiDetail {
  /** Nobody holds its chunk. */
  BACKGROUND,

  /** Someone holds its chunk, but no player is near. */
  REDUCED,

  /** A player is near, it is in a fight, or a player drives it. */
  FULL;

  fun atLeast(floor: AiDetail): AiDetail {
    return if (this < floor) floor else this
  }
}
