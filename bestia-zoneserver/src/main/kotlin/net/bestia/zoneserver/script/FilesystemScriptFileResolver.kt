package net.bestia.zoneserver.script

import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Paths

/**
 * This class looks up a path for a script file. It is determined by the name of
 * the script and the configured base dir of the script.
 *
 * @author Thomas Felix
 */
class FilesystemScriptFileResolver(
    private val basePath: String
): ScriptFileResolver {

  override fun getScriptInputStream(script: String): InputStream {
    val scriptPath = Paths.get(basePath, cleanScriptName(script))
    return FileInputStream(scriptPath.toFile())
  }
}
