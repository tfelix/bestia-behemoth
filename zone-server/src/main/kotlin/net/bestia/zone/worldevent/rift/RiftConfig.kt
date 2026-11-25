package net.bestia.zone.worldevent.rift

import org.springframework.boot.context.properties.ConfigurationProperties

/** How a rift grows, pulls and ends. `RiftCalibrationTest` checks the numbers feel as the design asks. */
@ConfigurationProperties("world-events.rift")
data class RiftConfig(

  /** Bestia days a knot gathers before it opens, rolled between the two. Time for players to find and prepare. */
  val knotMinBestiaDays: Double = 2.5,
  val knotMaxBestiaDays: Double = 4.5,

  /** Real hours an open rift stands before it collapses. */
  val openRealHours: Double = 24.0,

  /** How far a knot pulls, and how far an open rift pulls at its last stage, in metres. */
  val knotRadiusMetres: Double = 300.0,
  val openRadiusMetres: Double = 1500.0,

  /** Share of a cell's mana above [drainedDensity] a knot and an open rift pull in per real minute, at the centre. */
  val knotPullPerMinute: Double = 0.002,
  val openPullPerMinute: Double = 0.004,

  /** A rift drains the land to here and no further: the eerie calm around a knot. */
  val drainedDensity: Double = 0.05,

  /** A knot whose surroundings fall below this density loses its hold and dissolves. */
  val dissolveBelow: Double = 0.6,

  /** How far a collapse spills its mana, in metres. Heaviest at the centre. */
  val spillRadiusMetres: Double = 300.0,
)
