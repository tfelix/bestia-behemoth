package net.bestia.zone.quest.ecs

/** The personal objectives of one master's current stages, by what triggers them. */
class TrackedObjectives(
  val kills: List<TrackedKill> = emptyList(),
  val deeds: List<TrackedDeed> = emptyList(),
) {

  val isEmpty: Boolean
    get() = kills.isEmpty() && deeds.isEmpty()
}
