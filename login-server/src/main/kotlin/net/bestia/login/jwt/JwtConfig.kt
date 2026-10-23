package net.bestia.login.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "jwt")
@ConfigurationPropertiesScan
data class JwtConfig(
    val secret: String,
    val loginTokenMinutes: Long,
    val apiTokenMinutes: Long = 15,
    /**
     * Signs the companion app's tokens, and is shared with the api-server only. A key of its own, so the service
     * that checks them cannot mint a zone token: that one is signed with [secret].
     */
    val companionSecret: String = "",
    val companionTokenMinutes: Long = 15
)