package net.bestia.zoneserver.script

import java.util.Objects

internal class ScriptAnchor(name: String, functionName: String) {

  val scriptName: String?
  val functionName: String?

  /**
   * Creates a specialized string for safe the anchor.
   */
  val anchorString: String
    get() = String.format("%s:%s", scriptName, functionName)

  init {

    this.scriptName = Objects.requireNonNull(name)
    this.functionName = Objects.requireNonNull(functionName)
  }

  override fun toString(): String {
    return String.format("ScriptAnchor[name: %s, fn: %s]", scriptName, functionName)
  }

  override fun hashCode(): Int {
    return Objects.hash(scriptName, functionName)
  }

  override fun equals(obj: Any?): Boolean {
    if (this === obj)
      return true
    if (obj == null)
      return false
    if (javaClass != obj.javaClass)
      return false
    val other = obj as ScriptAnchor?
    if (functionName == null) {
      if (other!!.functionName != null)
        return false
    } else if (functionName != other!!.functionName)
      return false
    if (scriptName == null) {
      if (other.scriptName != null)
        return false
    } else if (scriptName != other.scriptName)
      return false
    return true
  }

  companion object {

    fun fromString(anchorStr: String): ScriptAnchor {
      val token = anchorStr.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      if (token.size != 2) {
        throw IllegalArgumentException("Invalid anchor string.")
      }

      return ScriptAnchor(token[0], token[1])
    }
  }
}