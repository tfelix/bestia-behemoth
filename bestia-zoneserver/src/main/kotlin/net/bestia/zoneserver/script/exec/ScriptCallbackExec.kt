package net.bestia.zoneserver.script.exec

/**
 * This script exec is used when a script is executed in an entity context. For example
 * an spawned item script, or an attack script which "lives" in an entity.
 */
data class ScriptCallbackExec private constructor(
    override val scriptKey: String,
    val callFunction: String,
    val uuid: String,
    val entityId: Long
) : ScriptExec {

  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["SCRIPT_UUID"] = uuid
    bindings["SELF"] = entityId
  }

  class Builder(
      val scriptCallFunction: String,
      val scriptEntityId: Long,
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
          uuid = uuid,
          entityId = scriptEntityId
      )
    }
  }
}