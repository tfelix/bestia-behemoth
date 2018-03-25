package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.script.api.ScriptRootApi
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
        private val scriptApi: ScriptRootApi
) {

  fun execute(fnName: String, script: CompiledScript, env: ScriptEnv) {
    val engine = script.engine

    // Setup the script environment.
    val bindings = engine.createBindings()
    env.setupEnvironment(scriptApi, bindings)
    engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE)

    val inv = script.engine as Invocable

    try {
      inv.invokeFunction(fnName)
    } catch (e: NoSuchMethodException) {
      LOG.error(e) { "Function $fnName is missing in script." }
    } catch (e: ScriptException) {
      LOG.error(e) { "Error during script execution." }
    }
  }
}
