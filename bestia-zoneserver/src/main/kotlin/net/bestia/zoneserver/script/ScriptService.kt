package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.script.exec.ScriptExec
import org.springframework.stereotype.Service
import javax.script.Bindings
import javax.script.Invocable
import javax.script.ScriptContext
import javax.script.SimpleBindings
import javax.script.SimpleScriptContext
import net.bestia.zoneserver.script.api.BestiaApi
import net.bestia.zoneserver.script.exec.ScriptCallbackExec

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
    val (bindings, rootApi) = setupEnvironment(scriptExec)

    LOG.debug { "Call Script: $scriptExec with bindings: ${bindings.entries}"  }

    val context = SimpleScriptContext()
    context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

    // Check if function invoke is needed or just call the script.
    val functionCall = when(scriptExec) {
      is ScriptCallbackExec -> scriptExec.callFunction
      else -> "main"
    }

    try {
      val runtimeScript = scriptCache.getScript(ScriptCache.RUNTIME_KEY)
      runtimeScript.eval(context);

      val script = scriptCache.getScript(scriptExec.scriptKey)

      script.eval(context)
      script.engine.context = context
      (script.engine as Invocable).invokeFunction(functionCall)

      scriptCommandProcessor.processCommands(rootApi.commands)
    } catch (e: NoSuchMethodException) {
      throw BestiaScriptException("Function $functionCall is missing in script ${scriptExec.scriptKey}", e)
    } catch (e: Exception) {
      throw BestiaScriptException("Error during script '${scriptExec.scriptKey}' execution.", e)
    }
  }

  private fun setupEnvironment(exec: ScriptExec): Pair<Bindings, BestiaApi> {
    val bindings = SimpleBindings()
    val rootApi = scriptRootApiFactory.buildScriptRootApi(bindings, exec)

    return Pair(bindings, rootApi)
  }
}
