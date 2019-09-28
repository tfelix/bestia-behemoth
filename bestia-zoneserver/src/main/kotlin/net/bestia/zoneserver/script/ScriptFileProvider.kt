package net.bestia.zoneserver.script

import org.springframework.core.io.Resource

interface ScriptFileProvider : Iterable<ScriptFile>

data class ScriptFile(
    val key: String,
    val resource: Resource
)