package net.bestia.zone.ai.core.state

/**
 * When an agent counts as off duty, so perception knows when to clear [CommonKeys.RESTED].
 *
 * Perception is the only thing that clears that latch and it runs over every agent in the world, so it
 * cannot ask what sort of creature this is. A species answers from its activity cycle and a person from the
 * hours they work, and those disagree: a night watchman is awake through exactly the hours a diurnal animal
 * sleeps. Asking the agent rather than its profile is what lets both be right.
 */
fun interface RestingWindow {

  fun isRestingAt(hour: Int, isNight: Boolean): Boolean

  companion object {
    /** For an agent that has no bedtime of its own; nothing will clear its latch. */
    val NEVER = RestingWindow { _, _ -> false }
  }
}
