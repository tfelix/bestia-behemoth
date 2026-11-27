package net.bestia.zone.doomsday.withering

import org.springframework.boot.context.properties.ConfigurationProperties

/** How the Withering spreads. Days are real days. */
@ConfigurationProperties("doomsday.withering")
data class WitheringConfig(
  /** How many corruption crystals stand when its clock runs out, if nobody breaks them. */
  val maxCrystals: Int = 300,
  val crystalsPerDay: Double = 6.0,

  /** How far one crystal corrupts the land, and how close two may grow. */
  val crystalReachMetres: Double = 2_000.0,
  val crystalSpacingMetres: Double = 1_200.0,

  /** How fast corruption rises under a crystal, and heals where none stands any more, per day. */
  val corruptPerDay: Double = 0.25,
  val healPerDay: Double = 0.05,

  /** How rotten the land must be for its plants to die, and how many cells lose theirs per run. */
  val plantsDieFrom: Double = 0.7,
  val blightCellsPerRun: Int = 20,

  /** A cleansing ritual heals this much within its radius, and wards the land for these real days. */
  val cleanseRadiusMetres: Double = 300.0,
  val cleanseAmount: Double = 0.6,
  val wardDays: Double = 7.0,

  /** A bestia masters kill on rotten land eases the rot of its cell by this. */
  val healPerKill: Double = 0.03,

  /** The crystals a Blight Bloom grows around its heart, and how far from it. */
  val bloomCrystals: Int = 5,
  val bloomReachMetres: Double = 2_500.0,

  /** The level of a crystal at onset and at the end of the clock. */
  val crystalLevelAtOnset: Int = 20,
  val crystalLevelAtEnd: Int = 70,
)
