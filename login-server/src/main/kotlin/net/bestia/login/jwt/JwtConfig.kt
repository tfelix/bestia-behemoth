package net.bestia.login.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "jwt")
@ConfigurationPropertiesScan
data class JwtConfig(
    val secret: String,
    val expirationDays: Int
)