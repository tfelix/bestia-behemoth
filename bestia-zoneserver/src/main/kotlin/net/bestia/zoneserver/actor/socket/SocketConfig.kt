package net.bestia.zoneserver.actor.socket

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
data class SocketConfig(
    @Value("\${zone.bind-address}")
    val bindAddress: String,
    @Value("\${zone.port}")
    val port: Int,
    @Value("\${zone.max-connections}")
    val maxConnections: Long
)