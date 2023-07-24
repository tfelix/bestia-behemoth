package net.bestia.zoneserver.script

data class CallbackContext(
    val methodName: String,
    val scriptContext: ScriptContext
)