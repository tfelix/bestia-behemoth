package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.model.geometry.Vec3
import net.bestia.model.item.PlayerItem
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.script.api.ScriptRootApi
import net.bestia.zoneserver.script.env.ScriptEnv
import org.springframework.stereotype.Service
import javax.script.*

private val LOG = KotlinLogging.logger { }

interface ScriptExec {
  val scriptKey: String
  fun setupEnvironment(bindings: MutableMap<String, Any?>)
}

data class ItemScriptExec private constructor(
    override val scriptKey: String,
    val userId: Long,
    val targetId: Long?,
    val targetPosition: Vec3? = null
) : ScriptExec {

  override fun setupEnvironment(bindings: MutableMap<String, Any?>) {
    bindings["SELF"] = userId
    bindings["TARGET_ENTITY"] = targetId
    bindings["TARGET_POSITION"] = targetPosition
  }

  class Builder() {
    var item: PlayerItem? = null
    var user: Entity? = null
    var targetEntity: Entity? = null
    var targetPoint: Vec3? = null

    fun build(): ItemScriptExec {

    }
  }
}

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
    val bindings = setupEnvironment(env)

    try {
      val script = scriptCache.getScript(scriptExec.scriptKey)
      script.engine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE)

      // Check if function invoke is needed or just call the script.

      (script.engine as Invocable).invokeFunction(fnName)
    } catch (e: NoSuchMethodException) {
      LOG.error(e) { "Function $fnName is missing in script $scriptName" }
    } catch (e: ScriptException) {
      LOG.error(e) { "Error during script execution." }
    }
  }

  private fun setupEnvironment(env: ScriptEnv): Bindings {
    val bindings = SimpleBindings()
    env.setupEnvironment(bindings)

    bindings["BESTIA"] = scriptRootApi

    return bindings
  }
}
