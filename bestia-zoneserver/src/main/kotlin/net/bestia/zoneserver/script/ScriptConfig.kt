package net.bestia.zoneserver.script

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
data class ScriptConfig(
    @Value("script.path")
    val scriptPath: String?
)