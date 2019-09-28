package net.bestia.zoneserver.script

import java.io.File

interface ScriptFileProvider : Iterable<ScriptFile>

data class ScriptFile(
    val key: String,
    val file: File
)