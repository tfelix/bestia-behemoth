package net.bestia.zoneserver.config

import net.bestia.zoneserver.actor.bootstrap.NodeBootStep
import net.bestia.zoneserver.script.ScriptService
import net.bestia.zoneserver.script.exec.BasicScriptExec
import org.springframework.stereotype.Component

@Component
class RunStartScriptBootStep(
    private val scriptService: ScriptService
) : NodeBootStep {
  override val bootStepName: String
    get() = "Start script"

  override fun execute() {
    val scriptExec = BasicScriptExec.Builder()
        .apply { scriptName = "startup" }
        .build()
    scriptService.execute(scriptExec)
  }
}