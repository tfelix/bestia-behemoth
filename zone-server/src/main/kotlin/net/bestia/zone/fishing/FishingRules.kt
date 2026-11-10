package net.bestia.zone.fishing

/** How far a line reaches and how long each step of a cast takes. */
data class FishingRules(
  val reachMetres: Long,
  val minWaitSeconds: Float,
  val maxWaitSeconds: Float,
  val strikeWindowSeconds: Float,
) {
  init {
    require(reachMetres > 0) { "reach-metres must be positive, got $reachMetres" }
    require(minWaitSeconds in 0f..maxWaitSeconds) { "wait seconds $minWaitSeconds..$maxWaitSeconds is no range" }
    require(strikeWindowSeconds > 0f) { "strike-window-seconds must be positive, got $strikeWindowSeconds" }
  }
}
