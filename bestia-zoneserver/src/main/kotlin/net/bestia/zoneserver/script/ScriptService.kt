package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.script.api.ScriptRootApi
import net.bestia.zoneserver.script.exec.ScriptExec
import org.springframework.stereotype.Service
import javax.script.*

private val LOG = KotlinLogging.logger { }

/**
 * This class is responsible for fetching the script, creating a appropriate
 * script binding context and then executing the called script.
 *
 *
 * It also provides the script API so the scripts can interact with the bestia
 * service.
 *
 * @author Thomas Felix
 */
@Service
class ScriptService(
    private val scriptCache: ScriptCache,
    private val scriptRootApi: ScriptRootApi
) {

  fun execute(scriptExec: ScriptExec) {
    LOG.trace { "Call Script: $scriptExec" }
    val bindings = setupEnvironment(scriptExec)
    try {
      val script = scriptCache.getScript(scriptExec.scriptKey)
      // Check if function invoke is needed or just call the script.
      val fnName = scriptExec.callbackFunction
      if (fnName != null) {
        scriptExec.callbackFunction?.let { fnName ->
          script.engine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE)
          (script.engine as Invocable).invokeFunction(fnName)
        }
      } else {
        script.eval(bindings)
      }
    } catch (e: NoSuchMethodException) {
      LOG.error(e) { "Function ${scriptExec.callbackFunction} is missing in script ${scriptExec.scriptKey}" }
    } catch (e: ScriptException) {
      LOG.error(e) { "Error during script execution." }
    }
  }

  private fun setupEnvironment(env: ScriptExec): Bindings {
    val bindings = SimpleBindings()
    env.setupEnvironment(bindings)

    bindings["Bestia"] = scriptRootApi

    return bindings
  }
}
