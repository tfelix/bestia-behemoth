package net.bestia.zone.ground

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Runtime settings for keeping clients in step with the ground marks they lay down themselves.
 *
 * A client derives footprints, wear and blood from what it sees, so the server only sends a column's marks when
 * it comes into view and then, rarely, a resync that corrects whatever the client got wrong.
 *
 * @property resyncIntervalSeconds Bestia seconds after the first change a client was not told about before the
 *   column is re-sent; later changes inside that window ride the same send. A real minute at `SPEED_FACTOR` 3.
 *   `0` never resyncs, so a client only learns a column's marks as it enters view, and is the setting to measure
 *   how much drift the clients actually accumulate.
 */
@ConfigurationProperties(prefix = "ground-sync")
data class GroundSyncConfig(
  val resyncIntervalSeconds: Long = 180,
) {

  init {
    require(resyncIntervalSeconds >= 0) { "resyncIntervalSeconds cannot be negative, was $resyncIntervalSeconds" }
  }

  val resyncs: Boolean
    get() {
      return resyncIntervalSeconds > 0
    }
}
