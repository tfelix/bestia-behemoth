package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.actor.bootstrap.NodeBootStep
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import kotlin.system.measureTimeMillis

private val LOG = KotlinLogging.logger { }

@Order(Ordered.HIGHEST_PRECEDENCE)
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
    val compileDurationMs = measureTimeMillis {
      fileProvider.forEach { scriptFile ->
        val compiled = scriptCompiler.compile(scriptFile.resource)
        LOG.debug { "Adding ${scriptFile.key}: ${scriptFile.resource.file.absolutePath}" }
        scriptCache.addScript(scriptFile.key, compiled)
        i++
      }
    }

    LOG.info { "Compiled $i scripts in $compileDurationMs ms" }
  }
}