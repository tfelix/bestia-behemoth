package net.bestia.zoneserver.actor.socket

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
data class SocketConfig(
    @Value("\${server.bind-address}")
    val bindAddress: String,
    @Value("\${server.port}")
    val port: Int,
    @Value("\${server.max-connections}")
    val maxConnections: Int
)