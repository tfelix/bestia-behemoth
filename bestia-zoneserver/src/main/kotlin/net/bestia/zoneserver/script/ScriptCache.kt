package net.bestia.zoneserver.script

import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.io.File
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

  private val cache = HashMap<String, CompiledScript>()

  private fun setupScript(scriptFile: File, key: String) {
    val compiledScript = compiler.compileScript(scriptFile)
    cache[key] = compiledScript
  }

  /**
   * Adds a folder to the script cache. It will immediately start to compile
   * all the scripts inside this folder.
   *
   * @param scriptBasePath The folder to add to the cache.
   */
  fun cacheFolder(scriptBasePath: Path) {

    LOG.info("Adding folder {} to script cache.", scriptBasePath)

    // Starting to compile the scripts.
    try {
      Files.newDirectoryStream(scriptBasePath).use { directoryStream ->
        for (scriptPath in directoryStream) {
          LOG.debug("Compiling script: {}", scriptPath)

          val scriptFile = resolver.getScriptFile(scriptPath.toString())
          val scriptKey = getRelativePath(scriptBasePath, scriptPath)

          setupScript(scriptFile, scriptKey)
        }
      }
    } catch (e: IOException) {
      LOG.error("Could not compile script.", e)
    }
  }

  private fun getRelativePath(basePath: Path, scriptPath: Path): String {
    return scriptPath.relativize(basePath).toString()
  }

  /**
   * Returns the compiled script of the given type and name.
   *
   * @param name The name of the scriptfile (without extention).
   * @return The compiled script or null of no script was found.
   */
  fun getScript(name: String): CompiledScript {
    Objects.requireNonNull(name)
    LOG.trace("Requesting script file from cache: {}.", name)

    if (!cache.containsKey(name)) {
      LOG.trace("Script was not found in cache. Compiling it first.")

      val scriptFile = resolver.getScriptFile(name)
      setupScript(scriptFile, name)
    }

    return cache[name]!!
  }
}