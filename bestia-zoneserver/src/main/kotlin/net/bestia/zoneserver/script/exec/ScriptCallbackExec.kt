package net.bestia.zoneserver.script.exec

import net.bestia.zoneserver.entity.Entity

/**
 * This script exec is used when a script is executed in an entity context. For example
 * an spawned item script, or an attack script which "lives" in an entity.
 */
data class ScriptCallbackExec private constructor(
    override val scriptKey: String,
    override val callFunction: String?,
    val selfId: Long,
    val uuid: String
) : ScriptExec {

  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["SELF"] = selfId
    bindings["UUID"] = uuid
  }

  class Builder(
      val ownerEntityId: Long,
      val scriptCallFunction: String,
      val uuid: String
  ) {
    fun build(): ScriptCallbackExec {
      val splittedScriptCallFn = scriptCallFunction.split("::")
      require(splittedScriptCallFn.size == 2) {
        "scriptCallFunction is not in the format 'scriptKey::callback'"
      }

      return ScriptCallbackExec(
          scriptKey = splittedScriptCallFn[0],
          callFunction = splittedScriptCallFn[1],
          selfId = ownerEntityId,
          uuid = uuid
      )
    }
  }
}