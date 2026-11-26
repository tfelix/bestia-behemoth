package net.bestia.zone.doomsday

/** How a threat shows itself, which decides how players hold it. */
enum class DoomShape {
  /** One thing that moves or grows from a point. */
  ACTOR,

  /** A field that spreads over the land. */
  FRONT,

  /** Bursts that come again and again, each one stronger. */
  PULSE,
}
