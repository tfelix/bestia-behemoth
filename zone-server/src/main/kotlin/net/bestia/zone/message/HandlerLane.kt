package net.bestia.zone.message

/** Which thread an incoming message is handled on. */
enum class HandlerLane {
  /** The tick thread, with the world to itself. For gameplay messages that need no database. */
  TICK,

  /** An IO thread, for handlers that need the database. They reach the world only through its scopes. */
  IO,
}
