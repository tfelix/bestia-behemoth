package net.bestia.zone.faction

import org.springframework.boot.context.properties.ConfigurationProperties

/** See the Factions page in the docs. */
@ConfigurationProperties(prefix = "faction")
data class FactionConfig(
  /** A master below this level cannot swear to an Order. */
  val minSwearLevel: Int = 40,
)
