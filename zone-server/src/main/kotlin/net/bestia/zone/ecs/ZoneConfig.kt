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
)
