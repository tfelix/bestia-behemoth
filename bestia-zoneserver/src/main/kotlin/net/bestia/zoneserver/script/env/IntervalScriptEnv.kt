package net.bestia.zoneserver.script.env

class IntervalScriptEnv(
        private val uuid: String
) : ScriptEnv() {
  override fun customEnvironmentSetup(bindings: MutableMap<String, Any?>) {
    bindings["SCRIPT_UUID"] = uuid
  }
}
