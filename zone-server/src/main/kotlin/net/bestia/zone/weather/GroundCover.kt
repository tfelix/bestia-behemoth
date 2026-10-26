package net.bestia.zone.weather

/**
 * What the weather has left on the ground around one player.
 *
 * Snow is given as two elevations rather than as the cover where the player stands, so a client can whiten
 * the mountain above a green valley. Both are null when no snow lies anywhere in the region.
 */
data class GroundCover(
  /** How wet the ground at the player's elevation is, 0 (dry) to 1 (soaked). */
  val wetness: Double,

  /** The elevation from which snow lies in patches. */
  val snowLine: Double?,

  /** The elevation from which snow covers the ground completely. */
  val snowFull: Double?,
) {
  companion object {
    val BARE = GroundCover(wetness = 0.0, snowLine = null, snowFull = null)
  }
}
