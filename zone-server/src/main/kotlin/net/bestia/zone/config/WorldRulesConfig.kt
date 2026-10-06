package net.bestia.zone.config

import net.bestia.zone.ecs.core.UndeclaredAccess
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "world")
data class WorldRulesConfig(
  val tickRate: Int,
  val parallelSystems: Boolean = false,
  val logoutProtectionSeconds: Float = 20f,
  /** Share of its current EXP an entity forfeits when it dies. */
  val deathExpLossFraction: Float = 0.01f,
  /** Longest path one move message may carry. The client sends legs of at most 24 tiles. */
  val maxMovePathSteps: Int = 64,
  /** How long a dropped plain item lies on the ground before it is gone. Real time, not Bestia time. */
  val groundItemDespawnAfter: Duration = Duration.ofDays(7),
  /**
   * Sustained ceiling on move requests from one account, and how many it may bank against a flurry of
   * clicking. See [net.bestia.zone.control.MoveRequestRateLimit] for why a move request is worth limiting.
   */
  val moveRequestsPerSecond: Float = 10f,
  val moveRequestBurst: Float = 20f,
  val undeclaredAccess: UndeclaredAccess = UndeclaredAccess.OFF,
)
