package net.bestia.zoneserver.script.exec

interface ScriptExec {
  val scriptKey: String
  fun setupEnvironment(bindings: MutableMap<String, Any?>)
}