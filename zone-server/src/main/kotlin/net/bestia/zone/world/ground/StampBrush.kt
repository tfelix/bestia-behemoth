package net.bestia.zone.world.ground

/**
 * Which shape a [GroundStamp] draws. The client owns the art; the server only says which.
 *
 * [wireId] is a contract and never reused, for [GroundLayer]'s reason: a decoder that disagrees draws the
 * wrong shape rather than failing, which nobody reports as a bug.
 *
 * Alternating [FOOT_LEFT] and [FOOT_RIGHT] is what makes a trail read as something *walking* rather than as a
 * dotted line. Everything without an obvious handedness uses [PAW], which is most of the bestiary.
 */
enum class StampBrush(val wireId: Int) {

  FOOT_LEFT(wireId = 1),
  FOOT_RIGHT(wireId = 2),

  /** Anything on four legs, and the fallback for a species with no gait of its own yet. */
  PAW(wireId = 3),

  HOOF(wireId = 4),

  BLOOD_SPLATTER(wireId = 5);

  companion object {

    fun ofWireId(wireId: Int): StampBrush? {
      return entries.firstOrNull { it.wireId == wireId }
    }
  }
}
