package net.bestia.zoneserver.script

data class ScriptAnchor(
        val name: String,
        val functionName: String) {

  /**
   * Creates a specialized string for safe the anchor.
   */
  val anchorString: String
    get() = String.format("%s:%s", name, functionName)

  companion object {
    fun fromString(anchorStr: String): ScriptAnchor {
      val token = anchorStr.split(":")
      if (token.size != 2) {
        throw IllegalArgumentException("Invalid anchor string.")
      }

      return ScriptAnchor(token[0], token[1])
    }
  }
}