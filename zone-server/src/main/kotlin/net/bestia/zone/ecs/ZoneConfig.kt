package net.bestia.zone.ecs

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@ConfigurationProperties(prefix = "world")
@ConfigurationPropertiesScan
data class ZoneConfig(
  val tickRate: Int,
  val parallelSystems: Boolean = false,
  val logoutProtectionSeconds: Float = 20f,
  /** Share of its current EXP an entity forfeits when it dies. */
  val deathExpLossFraction: Float = 0.01f,
  /** Longest path one move message may carry. The client sends legs of at most 24 tiles. */
  val maxMovePathSteps: Int = 64,
  /** How long a dropped plain item lies on the ground before it is gone. */
  val groundItemDespawnSeconds: Float = 600f,
)
