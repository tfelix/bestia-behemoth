package net.bestia.zone.ecs.core

/** What one registered system has cost since start. Read off the tick thread, by the metrics export. */
interface SystemStats {
  val name: String
  val phase: Phase
  val runs: Long
  val totalNanos: Long
  val failures: Long
  val disabled: Boolean
}
