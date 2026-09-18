package net.bestia.zone.world.spoor

/**
 * What a sweep of the ground found, already reduced to one account of it.
 *
 * The reduction is deliberate: a busy crossroads holds prints from a dozen things, and handing a player twelve
 * findings is a list rather than a reading. So this is the **heaviest** user of that ground - the one whose
 * tracks are most of what is there - with [walkers] left in to say how crowded it was.
 *
 * @property signature what left them, or null when nothing was recorded about it - which is what a track older
 *   than the index's memory looks like
 * @property passages how many prints that one left inside the search
 * @property ageSeconds Bestia seconds since its most recent print here
 * @property octant which way it went, as the modal heading of its prints. Eight-connected movement's own
 *   resolution, and steadier than any single print: a straight walk votes eight times for one direction.
 * @property walkers how many different things left prints inside the search
 */
data class TrackReading(
  val signature: ActorSignature?,
  val passages: Int,
  val ageSeconds: Long,
  val octant: Int,
  val walkers: Int,
)
