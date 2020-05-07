package net.bestia.zoneserver.script.exec

import net.bestia.zoneserver.script.ScriptKeyBuilder
import net.bestia.zoneserver.script.ScriptType

data class BasicScriptExec private constructor(
    override val scriptKey: String
) : ScriptExec {

  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    // no op
  }

  class Builder {
    var scriptName: String? = null

    fun build(): BasicScriptExec {
      require(scriptName != null) { "ScriptName must be given" }

      return BasicScriptExec(
          scriptKey = ScriptKeyBuilder.getScriptKey(ScriptType.BASIC, scriptName!!)
      )
    }
  }
}