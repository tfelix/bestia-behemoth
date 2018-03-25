package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.script.api.ScriptApi
import net.bestia.zoneserver.script.env.ScriptEnv
import org.springframework.stereotype.Service
import javax.script.CompiledScript
import javax.script.Invocable
import javax.script.ScriptContext
import javax.script.ScriptException

private val LOG = KotlinLogging.logger {  }

/**
 * Is responsible for attaching the bindings and executing the given script function.
 *
 * @author Thomas Felix
 */
@Service
class ScriptExecutionService(
        private val fnName: String,
        private val scriptApi: ScriptApi
) {

  /**
   * This prepares the script bindings for usage inside this script and
   * finally calls the given function name.
   */
  private fun callFunction(script: CompiledScript, functionName: String) {
    try {

      script.eval(script.engine.context)
      (script.engine as Invocable).invokeFunction(functionName)

    } catch (e: NoSuchMethodException) {
      LOG.error("Error calling script. Script does not contain {}() function.", functionName)
    } catch (e: ScriptException) {
      LOG.error("Error during script  {} execution.", e)
    }

  }

  fun execute(anchor: ScriptAnchor, script: CompiledScript, env: ScriptEnv) {
    val engine = script.engine

    // Setup the script environment.
    val bindings = engine.createBindings()
    env.setupEnvironment(scriptApi, bindings)

    engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE)

    val inv = script.engine as Invocable

    try {
      inv.invokeFunction(fnName)
    } catch (e: NoSuchMethodException) {
      LOG.error("Function {} is missing in script.", fnName, e)
    } catch (e: ScriptException) {
      LOG.error("Error during script execution.", e)
    }
  }
}
