package net.bestia.zone.world.mana

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How much mana each source releases. The numbers are set by `ManaCalibrationTest` from how the field should
 * feel, so change them there first.
 */
@ConfigurationProperties("mana.emission")
data class ManaEmissionConfig(

  /** Motes released per point of MP a cast costs. */
  val motesPerManaPoint: Double = 0.0055,

  /** Motes released per level of a slain bestia. */
  val motesPerKillLevel: Double = 0.04,

  /** How many times more a boss releases than its level alone. */
  val bossFactor: Double = 10.0,
)
