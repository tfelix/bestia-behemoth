package net.bestia.zoneserver.account

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
data class AuthenticationConfig(
    @Value("\${zone.root-auth-token}")
    val rootAuthToken: String?
)