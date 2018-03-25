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

    private const val MAIN_FUNC = "main"

    /**
     * To call into random scripts the name can be encoded like the following
     * scheme:
     *
     * <pre>
     * test - This will call the function test() in the original script file.
     * item/apple:test - This will call the function test in the script file item/apple.js
     * item/apple - This will call the default main function in script file item/apple.js
     * item/apple.js - Same as above.
    </pre> *
     *
     * @param scriptPath The name of the function to invoke.
     * @return A [ScriptAnchor] object containing all the needed entry
     * points to call into the js file.
     */
    @JvmStatic
    fun fromString(anchorStr: String): ScriptAnchor {
      val token = anchorStr.split(":").toTypedArray()
      val funcName = if (token.size == 2) {
        token[1]
      } else {
        MAIN_FUNC
      }

      var scriptName = token[0]

      // Remove .js from the end.
      if (scriptName.endsWith(".js")) {
        scriptName = scriptName.replace(".js", "")
      }

      // Remove trailing slash
      if (scriptName.startsWith("/")) {
        scriptName = scriptName.substring(1)
      }
      return ScriptAnchor(scriptName, funcName)
    }
  }
}