package net.bestia.zoneserver.script

import mu.KotlinLogging
import java.io.File
import java.net.URISyntaxException
import java.nio.file.Paths

private val LOG = KotlinLogging.logger { }

/**
 * This class looks up a path for a script file. It is determined by the name of
 * the script and the configured base dir of the script.
 *
 * @author Thomas Felix
 */
class ScriptFileResolver(scriptBasePath: String) {

  private val scriptBasePath: String
  private val isClasspath: Boolean = scriptBasePath.startsWith("classpath:")

  /**
   * Returns the global script file which contains helper and API access
   * helper.
   *
   * @return The global script file.
   */
  val globalScriptFile: File
    get() {
      val globalScriptFile = getScriptFromClasspath("helper.js")
      LOG.debug("Getting global script file: {}", globalScriptFile.absolutePath)
      return globalScriptFile
    }

  init {
    this.scriptBasePath = if (this.isClasspath) {
      scriptBasePath.substring("classpath:".length)
    } else {
      scriptBasePath
    }
  }

  /**
   * Returns the script file path for the path and the type.
   *
   * @param name The name of the script.
   * @return The path to the script.
   */
  fun getScriptFile(name: String): File {

    var cleanedName = name

    if (!cleanedName.endsWith(".js")) {
      cleanedName += ".js"
    }

    if (!cleanedName.startsWith("/")) {
      cleanedName = "/$name"
    }

    if (cleanedName.contains("..")) {
      throw IllegalArgumentException("Script path can not contain ..")
    }

    return if (isClasspath) {
      getScriptFromClasspath(name)
    } else {
      getScriptFromFolder(name)
    }
  }

  private fun getScriptFromFolder(scriptPath: String): File {
    val p = Paths.get(scriptBasePath, *scriptPath.split("\\/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
    return p.toFile()
  }

  private fun getScriptFromClasspath(scriptPath: String): File {
    val p = Paths.get("script", *scriptPath.split("\\/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
    try {
      val resource = ScriptFileResolver::class.java.getResource("/" + p.toString())
      return Paths.get(resource.toURI()).toFile()
    } catch (e: NullPointerException) {
      throw IllegalArgumentException("File does not exist: " + p.toString(), e)
    } catch (e: URISyntaxException) {
      throw IllegalArgumentException("File does not exist: " + p.toString(), e)
    }
  }
}
