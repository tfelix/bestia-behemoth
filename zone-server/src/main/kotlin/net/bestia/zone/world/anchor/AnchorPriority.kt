package net.bestia.zone.world.anchor

/** Which anchors keep their surroundings when there are more than the server will hold. First wins. */
enum class AnchorPriority {
  /** Somebody is looking through it; a client holds this ground anyway. */
  PLAYER,
  TRAVELLER,
  RESTING
}
