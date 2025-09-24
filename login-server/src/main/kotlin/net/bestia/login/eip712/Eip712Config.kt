package net.bestia.login.eip712

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@ConfigurationProperties(prefix = "eip712")
@ConfigurationPropertiesScan
data class Eip712Config(
  val name: String,
  val version: String ,
  val chainId: Long,
)