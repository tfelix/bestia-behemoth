package net.bestia.zone.doomsday.voidtear

import org.springframework.boot.context.properties.ConfigurationProperties

/** How hard the Void Tear presses on the world, from its onset to the end of its clock. Days are real days. */
@ConfigurationProperties("doomsday.void-tear")
data class VoidTearConfig(
  /** Mana it pours around itself per day, at onset and at the end of its clock. */
  val motesPerDayAtOnset: Double = 100_000.0,
  val motesPerDayAtEnd: Double = 600_000.0,
  val pourReachMetres: Double = 3_000.0,

  /** How far from the Tear it seeds rift knots, at onset and at the end of its clock. */
  val seedReachAtOnsetMetres: Double = 4_000.0,
  val seedReachAtEndMetres: Double = 30_000.0,
  val knotsPerDayAtOnset: Double = 0.5,
  val knotsPerDayAtEnd: Double = 4.0,

  /** A sealed Tear tears open again after this. */
  val reformDays: Double = 3.0,

  /** How far deeds push the clock: a knot it swallows brings the end closer, sealing it holds the end off. */
  val swallowPush: Double = 0.01,
  val sealPush: Double = -0.15,

  /** How much each rift sealed within its reach in the last week holds it back, up to all of it. */
  val containmentPerSeal: Double = 0.2,
  val containmentWindowDays: Double = 7.0,
)
