package net.bestia.zone.doomsday.destroyer

import org.springframework.boot.context.properties.ConfigurationProperties

/** How the Destroyer marches and how its deeds push the doom clock. Days are real days. */
@ConfigurationProperties("doomsday.destroyer")
data class DestroyerConfig(
  /** Its health against the boss it was, and what each razed town adds to that. */
  val hpMultiplier: Int = 20,
  val hpMultiplierPerTown: Int = 5,

  /** It razes a town once it stands this close to the town's centre. */
  val arriveMetres: Double = 80.0,

  /** It roams this far around the town it marches on, in voxels, so it reaches the centre and not only the edge. */
  val wanderVoxels: Long = 16,

  /** How far deeds push the clock: a razed town brings the end closer, bringing it down holds the end off. */
  val razePush: Double = 0.08,
  val defeatPush: Double = -0.10,

  /** A fallen Destroyer rises again at its lair after this, one town stronger. */
  val returnDays: Double = 3.0,

  /** A ritual at the lair of a fallen Destroyer keeps it down this long from then. */
  val resealDays: Double = 14.0,

  /** How long the last defeat holds it back, fading out over this many days. */
  val containmentDays: Double = 14.0,
)
