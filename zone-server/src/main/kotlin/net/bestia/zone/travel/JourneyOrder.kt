package net.bestia.zone.travel

/** Who ordered a [Journey], which decides whether the entity's own AI may interrupt it. */
enum class JourneyOrder {
  /** A player, in game or through the companion app. The AI waits until the journey ends. */
  PLAYER,

  /** The creature's own AI, which may drop the journey for something more pressing. */
  AI
}
