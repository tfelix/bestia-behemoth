package net.bestia.zone.blackboard

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "blackboard")
data class BlackboardConfig(

  /** The runs a poster may pick, in days, and the fee each costs in percent of the pay. A longer run costs more. */
  val feePercentByDays: Map<Int, Int> = mapOf(1 to 3, 3 to 5, 7 to 8),

  /** Quests one master may have open on the boards at once. */
  val maxOpenPerMaster: Int = 10,

  /** How long a withdrawn quest still takes hand-ins, so nobody on the way loses their pay. */
  val withdrawNotice: Duration = Duration.ofHours(1),

  /** Bounds a client also enforces, so a quest's pay always fits a stack of coins. */
  val maxAmount: Int = 9_999,
  val maxCoinsEach: Long = 100_000,
)
