package net.bestia.zoneserver.script.env

import net.bestia.zoneserver.script.api.ScriptApi

/**
 * The [ScriptEnv] creates and setup an execution environment of each
 * script. The script env should be immutable to ensure thread safety. All the
 * needed variables are setup before execution of the script since each script
 * type in the game has different environment variables which needs to be set.
 *
 * @author Thomas Felix
 */
abstract class ScriptEnv {

  fun setupEnvironment(scriptApi: ScriptApi, bindings: MutableMap<String, Any?>) {
    bindings["Bestia"] = scriptApi
    customEnvironmentSetup(bindings)
  }

  /**
   * Sets the script variables for this execution.
   */
  protected abstract fun customEnvironmentSetup(bindings: MutableMap<String, Any?>)
}
