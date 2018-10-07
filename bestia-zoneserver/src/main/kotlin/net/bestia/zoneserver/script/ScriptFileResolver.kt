package net.bestia.zoneserver.script

import java.io.InputStream

interface ScriptFileResolver {
  fun getScriptInputStream(script: String): InputStream
}

fun cleanScriptName(name: String): String {
  var cleanedName = name

  if (!cleanedName.endsWith(".js")) {
    cleanedName += ".js"
  }

  if (!cleanedName.startsWith("/")) {
    cleanedName = "/$cleanedName"
  }

  if (cleanedName.contains("..")) {
    throw IllegalArgumentException("Script path can not contain ..")
  }

  return cleanedName
}