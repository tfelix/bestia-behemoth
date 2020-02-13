package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.script.exec.ScriptExec
import org.springframework.stereotype.Service
import javax.script.Bindings
import javax.script.Invocable
import javax.script.ScriptContext
import javax.script.SimpleBindings
import javax.script.SimpleScriptContext
import net.bestia.zoneserver.script.api.ScriptRootApi

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
    private val scriptCommandProcessor: ScriptCommandProcessor,
    private val scriptRootApiFactory: ScriptRootApiFactory
) {

  // Enable per thread context here https://stackoverflow.com/questions/33620318/executing-a-function-in-a-specific-context-in-nashorn
  @Synchronized
  fun execute(scriptExec: ScriptExec) {
    LOG.trace { "Call Script: $scriptExec" }
    val (bindings, rootApi) = setupEnvironment(scriptExec)

    val context = SimpleScriptContext()

    try {
      val script = scriptCache.getScript(scriptExec.scriptKey)
      // TODO Improve this binding handling and make it thread safe
      val globalBindings = script.engine.getBindings(ScriptContext.ENGINE_SCOPE)
      context.setBindings(bindings, ScriptContext.ENGINE_SCOPE)
      context.setBindings(globalBindings, ScriptContext.GLOBAL_SCOPE)

      // Check if function invoke is needed or just call the script.
      scriptExec.callFunction?.let { fnName ->
        script.eval(context) // < Broken! Fix context handling
        (script.engine as Invocable).invokeFunction(fnName)
      } ?: run {
        script.eval(context)
      }

      scriptCommandProcessor.processCommands(rootApi.commands)
    } catch (e: NoSuchMethodException) {
      throw BestiaScriptException("Function ${scriptExec.callFunction} is missing in script ${scriptExec.scriptKey}", e)
    } catch (e: Exception) {
      throw BestiaScriptException("Error during script '${scriptExec.scriptKey}' execution.", e)
    }
  }

  private fun setupEnvironment(exec: ScriptExec): Pair<Bindings, ScriptRootApi> {
    val bindings = SimpleBindings()
    val rootApi = scriptRootApiFactory.buildScriptRootApi(bindings, exec)

    return Pair(bindings, rootApi)
  }
}
