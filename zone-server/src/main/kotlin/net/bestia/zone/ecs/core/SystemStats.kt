package net.bestia.zone.ecs.core

/** What one registered system has cost since start. Read off the tick thread, by the metrics export. */
interface SystemStats {
  val name: String
  val phase: Phase
  val runs: Long
  val totalNanos: Long

  /**
   * The longest single update in the last completed window of [SystemScheduler.MAX_WINDOW_NANOS], 0 when it did
   * not run in it. A mean hides a system that is fast every tick but one in a hundred; this is what shows it.
   * A window is long enough that a scrape every few seconds sees each of them.
   */
  val maxNanosLastWindow: Long
  val failures: Long
  val disabled: Boolean
}
