package net.bestia.zoneserver.script

class ScriptKeyBuilder() {
  fun getScriptKey(type: ScriptType, scriptName: String): String {
    return "${type.name.toLowerCase()}_$scriptName"
  }
}