package net.bestia.zoneserver.script

import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.script.CompiledScript

private val LOG = KotlinLogging.logger { }

/**
 * The script cache will accept folders which contain java scripts. It will
 * start to compile the folder content and save the compiled script for later
 * use. The scripts can be reused but keep in mind that the scripts are usually
 * not thread safe and should store no internal persistent state.
 *
 * @author Thomas Felix
 */
@Component
class ScriptCache(
    private val compiler: ScriptCompiler,
    private val resolver: ScriptFileResolver
) {

  private val cache = mutableMapOf<String, CompiledScript>()

  /**
   * Adds a folder to the script cache. It will immediately start to compile
   * all the scripts inside this folder.
   *
   * @param scriptBasePath The folder to add to the cache.
   */
  fun cacheFolder(scriptBasePath: Path) {

    LOG.warn {
      "Script folder caching is currently disabled until we figured out how to properly" +
          " read folder from resource."
    }
  }

  /**
   * Returns the compiled script of the given type and name.
   *
   * @param name The name of the script file (without extention).
   * @return The compiled script or null of no script was found.
   */
  @Throws(IOException::class)
  fun getScript(name: String): CompiledScript {
    LOG.trace("Requesting script file from cache: {}.", name)

    val script = cache[name]

    return when (script) {
      null -> {
        LOG.trace("Script was not found in cache. Try compiling it first.")
        val scriptFile = resolver.getScriptInputStream(name)
        val compiledScript = compiler.compileScript(scriptFile) ?: throw IOException()
        cache[name] = compiledScript
        compiledScript
      }
      else -> script
    }
  }
}