package net.bestia.zone.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** What a job action (a craft, today) pays in EXP, as a share of the level its time is worth on [ExpCurveConfig]. */
@ConfigurationProperties(prefix = "work-exp")
data class WorkExpConfig(
  /** The share of a fighter's rate a master who does nothing but work reaches. Below 1: walking and gathering are not work. */
  val pace: Double = 0.75,
  /** The most of a level one payout may be, however long the action. */
  val payoutCapShare: Double = 0.35,
  /** How much effort may be banked while the master does not work, online or off. */
  val bankSeconds: Int = 3600,
) {
  init {
    require(pace > 0 && pace <= 1) { "work-exp.pace must be within (0, 1], was $pace" }
    require(payoutCapShare > 0 && payoutCapShare <= 1) { "work-exp.payout-cap-share must be within (0, 1], was $payoutCapShare" }
    require(bankSeconds > 0) { "work-exp.bank-seconds must be positive, was $bankSeconds" }
  }
}
