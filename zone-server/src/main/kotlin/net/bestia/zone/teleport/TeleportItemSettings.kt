package net.bestia.zone.teleport

import org.springframework.boot.context.properties.ConfigurationProperties

/** How the fly wing and the butterfly wing behave. Timings are the client's effect lengths too. */
@ConfigurationProperties(prefix = "teleport-items")
data class TeleportItemSettings(
  /**
   * How far a fly wing may carry its user. Inside the client's view radius (160 m), so the landing spot is on
   * ground it already draws and the hop needs no loading screen.
   */
  val flyWingRadiusMetres: Long = 150,

  /** The shortest hop, so a fly wing never looks like it did nothing. */
  val flyWingMinMetres: Long = 20,

  val flyWingVanishMs: Int = 250,
  val flyWingAppearMs: Int = 250,
  val butterflyWingVanishMs: Int = 900,
  val butterflyWingAppearMs: Int = 600,
)
