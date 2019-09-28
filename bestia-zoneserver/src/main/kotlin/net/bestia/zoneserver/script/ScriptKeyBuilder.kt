package net.bestia.zoneserver.script

object ScriptKeyBuilder {

  private val fileEndingPattern = """\.[^.]+$""".toRegex()

  fun getScriptKey(type: ScriptType, scriptName: String): String {
    val cleanedName = scriptName.replace(fileEndingPattern, "")
    return "${type.name.toLowerCase()}_$cleanedName"
  }
}