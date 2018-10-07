package net.bestia.zoneserver.script

import java.io.InputStream
import java.lang.Exception

class ClasspathScriptFileResolver(
     _basePath: String
) : ScriptFileResolver {

  private val basePath = _basePath.let {
    if(!it.startsWith("classpath:")) {
      throw IllegalArgumentException("Basepath must start with classpath:")
    }

    var stripedPath = it.removePrefix("classpath:")

    if(!stripedPath.startsWith("/")) {
      stripedPath = "/$stripedPath"
    }

    if(stripedPath.endsWith("/")) {
      stripedPath = stripedPath.removeSuffix("/")
    }

    stripedPath
  }

  override fun getScriptInputStream(script: String): InputStream {
    val path = "$basePath${cleanScriptName(script)}"
    try {
      return this.javaClass.getResourceAsStream(path)
    } catch (e: Exception) {
      throw IllegalArgumentException("File does not exist: $path", e)
    }
  }
}