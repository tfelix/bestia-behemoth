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

  /** Motes per item a broken store releases, by item identifier. */
  val storedMotes: Map<String, Double> = mapOf("mana_crystal" to 10.0),

  /** Motes a Wound pours out per real day once the world is old. */
  val woundMotesPerDay: Double = 50_000.0,

  /** How far around a Wound its mana falls, in metres. The worldgen corruption reaches as far. */
  val woundReachMetres: Double = 2500.0,

  /** The share of the full output a Wound has when the world is new. */
  val woundYoungShare: Double = 0.1,

  /** Real days until a Wound pours at full output. */
  val woundFullAgeDays: Double = 180.0,
)
