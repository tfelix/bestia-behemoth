package net.bestia.zoneserver.script

import java.io.InputStream
import java.net.URISyntaxException
import java.nio.file.Paths

class ClasspathScriptFileResolver(
    private val basePath: String
) : ScriptFileResolver {

  override fun getScriptInputStream(script: String): InputStream {
    val p = Paths.get(basePath, cleanScriptName(script))
    try {
      return this.javaClass.getResourceAsStream(p.toString())
    } catch (e: NullPointerException) {
      throw IllegalArgumentException("File does not exist: " + p.toString(), e)
    } catch (e: URISyntaxException) {
      throw IllegalArgumentException("File does not exist: " + p.toString(), e)
    }
  }
}