package net.bestia.zoneserver.script.env

class IntervalScriptEnv(
    private val uuid: String
) : ScriptEnv {
  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["SCRIPT_UUID"] = uuid
  }
}
