package net.bestia.zone.world.mana

import org.springframework.boot.context.properties.ConfigurationProperties

/** How fast the mana play adds or takes fades, creeps and drains out of the start towns. */
@ConfigurationProperties("mana.field")
data class ManaFieldConfig(

  /** Real days until half of any added or taken mana is gone again. */
  val leakHalfLifeDays: Double = 30.0,

  /** How far mana spreads from where it was added, in metres per real week. */
  val creepMetresPerWeek: Double = 300.0,

  /** The density the start towns drain added mana down to. */
  val sanctuaryTarget: Double = 0.3,

  /** Bestia days until half of the mana above [sanctuaryTarget] has drained out of a start town. */
  val sanctuaryHalfLifeBestiaDays: Double = 3.0,

  /** How far around a start town its drain reaches, until provinces exist. */
  val sanctuaryRadiusMetres: Double = 3000.0,
)
