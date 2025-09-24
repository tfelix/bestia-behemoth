package net.bestia.zone

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@ConfigurationProperties(prefix = "zone")
@ConfigurationPropertiesScan
data class ZoneConfig(
  val bestiaBaseSlotCount: Int,
  val bestiaMaxSlotCount: Int,
  val jwtAuthSecretKey: String,
  val shardId: Int,
  val allowExpiredTokens: Boolean
)
