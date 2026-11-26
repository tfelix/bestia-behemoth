package net.bestia.zone.blackboard

/**
 * The running world events a board posts, so masters in town hear of them. Nobody signs a notice: whoever helps
 * shares the loot and the honor. Implemented by the world events, which sit above the boards.
 */
fun interface EventNotices {

  fun postedAt(x: Long, y: Long): List<Notice>

  /**
   * @property eventId the `world_events.yml` id, which the client names
   * @property placeName the place it runs in
   * @property x where it runs, in metres
   * @property stage how far along it is: 1 stirring, 2 gathering, 3 about to break
   */
  data class Notice(
    val eventId: String,
    val placeName: String,
    val x: Long,
    val y: Long,
    val stage: Int,
  )
}
