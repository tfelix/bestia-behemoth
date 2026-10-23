package net.bestia.zone.world.anchor

/**
 * Which anchors keep their surroundings when there are more than the server will hold.
 *
 * Nothing is cut until the zone has more anchors than `simulation-anchors.max-holding-anchors` (512 by default).
 * Above it [AnchorResidencySystem] keeps the most important ones: earlier entries first, then the lower entity id.
 * The rest hold no ground resident and leave [SimulationAnchorIndex], so the AI near them drops out of full
 * detail, until enough anchors ahead of them go. Spawners still wake around every anchor ([AnchorPositions]).
 */
enum class AnchorPriority {
  /**
   * A connected client is looking through this entity: it has `ActivePlayer`. Never stored, worked out each tick.
   * Losing its place costs almost nothing: the client's own subscription keeps its ground resident, and the AI
   * around it only drops from full to reduced detail.
   */
  ACTIVE_CLIENT,

  /** On a journey, so the server keeps simulating the ones on the move. */
  TRAVELLER,

  RESTING
}
