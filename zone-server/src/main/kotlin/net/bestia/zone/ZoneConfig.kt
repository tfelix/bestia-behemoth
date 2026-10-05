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
  val partyNameMaxLength: Int = 20,
  /** How long a kick refuses older login tokens. Must outlast the login token's lifetime. */
  val kickMemorySeconds: Long = 300
)
