package net.bestia.zone.doomsday.foreshadow

import org.springframework.boot.context.properties.ConfigurationProperties

/** How hard the Mana Storm Season floods its province. Days are days of world age. */
@ConfigurationProperties("doomsday.mana-storm-season")
data class ManaStormSeasonConfig(
  val motesPerDay: Double = 400_000.0,
  val reachMetres: Double = 5_000.0,

  /** Omen scars it leaves around its heart when nobody stopped it. */
  val scars: Int = 6,
  val scarRadiusMetres: Double = 3_000.0,
)
