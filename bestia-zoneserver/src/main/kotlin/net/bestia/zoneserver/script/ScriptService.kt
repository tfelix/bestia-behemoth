package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.script.api.BestiaApiFactory
import org.springframework.stereotype.Service

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
    private val apiFactory: BestiaApiFactory
) {
  fun execute(scriptContext: ScriptContext, scriptCommandProcessor: ScriptCommandProcessor) {
    val api = apiFactory.buildScriptRootApi(scriptContext)

    try {
      val runtimeScript = scriptCache.getScriptInstance(scriptContext)

      val scriptExec = scriptContext.toScriptExec()
      runtimeScript.execute(api, scriptExec)

      scriptCommandProcessor.processCommands(api.messages)
    } catch (e: Exception) {
      throw BestiaScriptException("Error during script '${scriptContext}' execution.", e)
    }
  }
}
