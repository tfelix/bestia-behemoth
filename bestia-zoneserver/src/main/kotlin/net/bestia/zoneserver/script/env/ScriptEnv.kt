package net.bestia.zoneserver.script.env

/**
 * The [ScriptEnv] creates and setup an execution environment of each
 * script. The script env should be immutable to ensure thread safety. All the
 * needed variables are setup before execution of the script since each script
 * type in the game has different environment variables which needs to be set.
 *
 * @author Thomas Felix
 */
interface ScriptEnv {

  fun setupEnvironment(bindings: MutableMap<String, Any?>)
}
