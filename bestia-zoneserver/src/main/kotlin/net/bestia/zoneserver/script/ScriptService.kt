package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.IdGenerator
import net.bestia.zoneserver.entity.factory.MobFactory
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
    private val mobFactory: MobFactory,
    private val messageApi: MessageApi,
    private val idGenerator: IdGenerator,
    private val entityCollisionService: EntityCollisionService
) {

  // All script execution should be done inside a own actor to avoid blocking the system

  fun execute(scriptExec: ScriptExec) {
    LOG.trace { "Call Script: $scriptExec" }
    val (bindings, rootApi) = setupEnvironment(scriptExec)
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

      rootApi.commitEntityUpdates(messageApi)
    } catch (e: NoSuchMethodException) {
      LOG.error(e) { "Function ${scriptExec.callbackFunction} is missing in script ${scriptExec.scriptKey}" }
    } catch (e: ScriptException) {
      LOG.error(e) { "Error during script execution." }
    }
  }

  private fun setupEnvironment(exec: ScriptExec): Pair<Bindings, ScriptRootApi> {
    val bindings = SimpleBindings()

    exec.setupEnvironment(bindings)

    val rootApi = ScriptRootApi(
        scriptName = exec.scriptKey,
        idGeneratorService = idGenerator,
        mobFactory = mobFactory,
        entityCollisionService = entityCollisionService
    )
    bindings["Bestia"] = rootApi

    return Pair(bindings, rootApi)
  }
}
