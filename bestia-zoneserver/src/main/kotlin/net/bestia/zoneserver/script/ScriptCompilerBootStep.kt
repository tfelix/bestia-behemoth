package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.actor.bootstrap.NodeBootStep
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
class ScriptCompilerBootStep(
    private val fileProvider: ScriptFileProvider,
    private val scriptCompiler: ScriptCompiler,
    private val scriptCache: ScriptCache
) : NodeBootStep {
  override val bootStepName: String
    get() = "Compile scripts"

  override fun execute() {
    var i = 0
    fileProvider.forEach { scriptFile ->
      val compiled = scriptCompiler.compile(scriptFile.file)
      scriptCache.addScript(scriptFile.key, compiled)
      i++
    }
    LOG.info { "Compiled $i scripts" }
  }
}