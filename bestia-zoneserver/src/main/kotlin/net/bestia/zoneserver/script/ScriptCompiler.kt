package net.bestia.zoneserver.script

import mu.KotlinLogging
import net.bestia.zoneserver.script.env.GlobalEnv
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileReader
import java.io.IOException
import javax.script.*

private val LOG = KotlinLogging.logger { }

/**
 * The ScriptCache is responsible for reading and compiling all the scripts for
 * the system.
 *
 * @author Thomas Felix
 */
@Component
class ScriptCompiler(globalEnv: GlobalEnv) {

  private val engine: ScriptEngine = ScriptEngineManager().getEngineByName("nashorn")

  init {
    LOG.info { "Starting script engine: ${engine.factory.engineName} (version ${engine.factory.engineVersion})." }

    val bindings = engine.createBindings()
    globalEnv.setupEnvironment(bindings)
    engine.setBindings(bindings, ScriptContext.GLOBAL_SCOPE)
  }

  /**
   * Tries to compile the given script file.
   *
   * @param file The javascript bestia script file.
   * @return A compiled version of the script or null if there was an error.
   */
  fun compileScript(file: File): CompiledScript? {
    LOG.trace("Compiling script file: {}.", file)

    try {
      FileReader(file).use { scriptReader ->

        val script = (engine as Compilable).compile(scriptReader)
        script.eval()
        return script

      }
    } catch (e: ScriptException) {
      LOG.error("Could not compile script.", e)
      return null
    } catch (e: IOException) {
      LOG.error("Could not compile script.", e)
      return null
    }
  }
}