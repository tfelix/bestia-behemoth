package net.bestia.zone.travel

/** Why [JourneyService.start] would not send an entity off. */
enum class JourneyRefusal {
  NO_DESTINATION,
  TOO_MANY_WAYPOINTS,
  NOT_IN_WORLD,
  DEAD,

  /** A waypoint stands in water too deep to wade. */
  IN_WATER
}
