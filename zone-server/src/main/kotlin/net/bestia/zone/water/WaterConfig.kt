package net.bestia.zone.water

import org.springframework.boot.context.properties.ConfigurationProperties

/** How much of each tick the water simulation may spend, and how much of the world it may hold. */
@ConfigurationProperties("water")
data class WaterConfig(

  /** Cells updated per step, at about 95 ns each (`WaterStepCalibrationTest`): about a millisecond. */
  val cellsPerStep: Int = 10_000,

  /** Chunks held at most, at about 700 kB each. Water pressing past them stops as if against a wall. */
  val maxChunks: Int = 64,

  /** Chunks read in per step. Reading one merges and generates it, a few milliseconds each. */
  val chunkLoadsPerStep: Int = 1,

  /** Chunks committed per step. Each commit re-encodes the chunk and queues a patch and a rebuild. */
  val commitsPerStep: Int = 2,

  /** Real seconds between two commits of one chunk while its water still moves. */
  val commitIntervalSeconds: Float = 1f,
)
