package net.bestia.zoneserver.script

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.ScriptComponent
import net.bestia.zoneserver.script.env.SimpleScriptEnv
import org.springframework.stereotype.Service

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
        private val scriptExecService: ScriptExecService
) {

  /**
   * Central entry point for calling a script execution from the Bestia
   * system. This will fetch the script from cache, if cache does not hold the
   * script it will attempt to compile it. It will then set the script
   * environment and execute its main function.
   *
   * @param name The name of the script to be called.
   */
  fun callScriptMainFunction(name: String) {
    val anchor = ScriptAnchor.fromString(name)

    scriptExecService.executeFunction(
            SimpleScriptEnv(),
            anchor.name,
            "main"
    )
  }

  /**
   * The script callback is triggered via a counter which was initially set
   * into the [ScriptComponent].
   *
   * @param scriptUuid     The uuid of the script (an entity can have more then one
   * callback script attached).
   */
  fun callScriptIntervalCallback(entity: Entity, scriptUuid: String) {
    val scriptComp = entity.tryGetComponent(ScriptComponent::class.java) ?: return
    val scriptAnchorString = scriptComp.scripts[scriptUuid]?.script ?: return
    val anchor = ScriptAnchor.fromString(scriptAnchorString)

    scriptExecService.executeFunction(
            SimpleScriptEnv(),
            anchor.name,
            anchor.functionName
    )
  }
}
