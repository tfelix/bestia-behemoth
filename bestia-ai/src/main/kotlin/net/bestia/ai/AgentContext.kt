package net.bestia.ai

import java.time.Instant

data class AgentContext(
    val id: String,
    var lastTick: Instant
)