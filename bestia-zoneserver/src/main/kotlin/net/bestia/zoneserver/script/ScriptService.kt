package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.ScriptComponent
import net.bestia.zoneserver.script.env.ScriptEnv
import net.bestia.zoneserver.script.env.SimpleScriptEnv
import org.springframework.stereotype.Service
import java.util.*
import javax.script.CompiledScript
import javax.script.Invocable
import javax.script.ScriptException

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
    private val cache: ScriptCache
) {

  fun execute(fnName: String, script: CompiledScript, env: ScriptEnv) {
    val engine = script.engine
    val bindings = engine.createBindings()
    env.setupEnvironment(bindings)
    val inv = script.engine as Invocable
    try {
      script.eval(bindings)
      inv.invokeFunction(fnName)
    } catch (e: NoSuchMethodException) {
      LOG.error(e) { "Function $fnName is missing in script $script" }
    } catch (e: ScriptException) {
      LOG.error(e) { "Error during script execution." }
    }
  }

  private fun resolveScript(scriptAnchor: ScriptAnchor): CompiledScript {
    return cache.getScript(scriptAnchor.name)
  }

  /**
   * Central entry point for calling a script execution from the Bestia
   * system. This will fetch the script from cache, if cache does not hold the
   * script it will attempt to compile it. It will then set the script
   * environment and execute its main function.
   *
   * @param name The name of the script to be called.
   */
  fun callScriptMainFunction(name: String) {
    Objects.requireNonNull(name)
    LOG.debug("Calling script: {}.", name)

    val ident = ScriptAnchor.fromString(name)
    val script = resolveScript(ident)

    val scriptEnv = SimpleScriptEnv()

    execute(ident.functionName, script, scriptEnv)
  }

  /**
   * The script callback is triggered via a counter which was initially set
   * into the [ScriptComponent].
   *
   * @param scriptUuid     The uuid of the script (an entity can have more then one
   * callback script attached).
   */
  fun callScriptIntervalCallback(entity: Entity, scriptUuid: String) {

    LOG.trace { "Script $entity interval called." }

    val scriptComp = entity.getComponent(ScriptComponent::class.java)
    val scriptAnchorString = scriptComp.scripts[scriptUuid]?.script ?: return
    val anchor = ScriptAnchor.fromString(scriptAnchorString)
    val script = resolveScript(anchor)
    val simpleScriptEnv = SimpleScriptEnv()

    execute(anchor.functionName, script, simpleScriptEnv)
  }
}
