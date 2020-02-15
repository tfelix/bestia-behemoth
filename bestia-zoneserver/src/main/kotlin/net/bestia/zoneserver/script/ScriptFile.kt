package net.bestia.zoneserver.script

import org.springframework.core.io.Resource

data class ScriptFile(
    val key: String,
    val resource: Resource
)