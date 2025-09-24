package net.bestia.zone.ecs

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@ConfigurationProperties(prefix = "world")
@ConfigurationPropertiesScan
data class ZoneConfig(
  val tickRate: Int
)
