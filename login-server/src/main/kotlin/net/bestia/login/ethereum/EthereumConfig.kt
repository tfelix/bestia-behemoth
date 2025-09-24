package net.bestia.login.ethereum

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "ethereum")
@ConfigurationPropertiesScan
data class EthereumConfig(
    val rpcUrl: String,
    val nftContractAddress: String
)