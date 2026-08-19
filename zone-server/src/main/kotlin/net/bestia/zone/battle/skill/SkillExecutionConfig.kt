package net.bestia.zone.battle.skill

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bounds on what one skill script may do to the world.
 *
 * A script runs off the tick thread (see [SkillExecutionService]) but reaches the world through the
 * same lock the tick holds, so the cost it imposes is *lock occupancy* rather than tick time. These
 * are the two ceilings on that.
 *
 * @property worldOpsPerCast world operations one cast may spend. Each opens its own lock scope, so this is
 *   what bounds how often a cast interrupts the tick. Generous against what the current scripts need (the
 *   busiest, a crafting activation, spends four) and low enough that a loop is caught long before it matters.
 * @property maxQueryResults entities one spatial query may return, charged per result on top of the query
 *   itself. Beyond this the query is refused rather than truncated, because a silently short answer is worse
 *   than a fizzled cast. Must stay below [worldOpsPerCast] or the op budget always trips first and this
 *   never reports anything - which is why the two are checked against each other here.
 * @property maxMillisPerCast wall clock one cast may take, checked when an operation is charged. It catches a
 *   script looping *between* operations; it cannot interrupt one already running, so it is a runaway guard
 *   rather than the bound. [worldOpsPerCast] is what pins behaviour, and what a test should assert against.
 */
@ConfigurationProperties("skill")
data class SkillExecutionConfig(
  val worldOpsPerCast: Int = 64,
  val maxQueryResults: Int = 32,
  val maxMillisPerCast: Long = 250,
) {

  init {
    require(worldOpsPerCast > 0) { "worldOpsPerCast must be > 0, was $worldOpsPerCast" }
    require(maxMillisPerCast > 0) { "maxMillisPerCast must be > 0, was $maxMillisPerCast" }
    require(maxQueryResults in 1 until worldOpsPerCast) {
      "maxQueryResults ($maxQueryResults) must be in 1..${worldOpsPerCast - 1}: a query is charged per " +
        "result, so at or above worldOpsPerCast the op budget always trips first and the cap is dead"
    }
  }
}
