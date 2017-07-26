package net.bestia.zoneserver.script;

import org.springframework.stereotype.Component;

@Component
class ScriptResolver {

	private static final String MAIN_FUNC = "main";

	/**
	 * To call into random scripts the name can be encoded like the following
	 * scheme:
	 * 
	 * <pre>
	 * test - This will call the function test() in the original script file.
	 * item/apple:test - This will call the function test in the script file item/apple.js
	 * item/apple - This will call the default main function in script file item/apple.js
	 * item/apple.js - Same as above.
	 * </pre>
	 * 
	 * @param callback
	 * @return A {@link ScriptIdent} object containing all the needed entry
	 *         points to call into the js file.
	 */
	public ScriptIdent resolveScriptIdent(String callbackName) {

		String[] token = callbackName.split(":");
		String funcName;

		if (token.length == 2) {
			funcName = token[1];
		} else {
			funcName = MAIN_FUNC;
		}

		String scriptName = token[0];

		// Remove .js from the end.
		if (scriptName.endsWith(".js")) {
			scriptName = scriptName.replace(".js", "");
		}

		// Remove trailing slash
		if (scriptName.startsWith("/")) {
			scriptName = scriptName.substring(1);
		}

		// Detect the type.
		ScriptType type;

		String[] nameTokens = scriptName.toUpperCase().split("/");
		if (nameTokens.length == 1) {
			type = ScriptType.NONE;
		} else {
			if (nameTokens[0].startsWith("ITEM")) {
				type = ScriptType.ITEM;
			} else if (nameTokens[0].startsWith("ATTACK")) {
				type = ScriptType.ATTACK;
			} else if (nameTokens[0].startsWith("STATUS_EFFECT")) {
				type = ScriptType.STATUS_EFFECT;
			} else if (nameTokens[0].startsWith("MAP")) {
				type = ScriptType.MAP;
			} else {
				throw new IllegalArgumentException("Unknown script type: " + nameTokens[0]);
			}
		}

		return new ScriptIdent(type, scriptName, funcName);
	}

}
