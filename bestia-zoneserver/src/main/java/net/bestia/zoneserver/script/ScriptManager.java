package net.bestia.zoneserver.script;

import javax.script.Bindings;
import javax.script.CompiledScript;

/**
 * This class loads directories of scripts. And saves them in a compiled form to
 * let them be executed later.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptManager {

	private final ScriptCompiler compiler;
	private Bindings bindings;

	public ScriptManager(ScriptCompiler compiler) {
		if (compiler == null) {
			throw new IllegalArgumentException("Compiler can not be null.");
		}

		this.compiler = compiler;
	}

	/**
	 * Returns the underlying {@link ScriptCompiler}.
	 * 
	 * @return The {@link ScriptCompiler}.
	 */
	public ScriptCompiler getCompiler() {
		return compiler;
	}

	public void setStdBindings(Bindings bindings) {

		if (bindings == null) {
			throw new IllegalArgumentException("Bindings can not be null.");
		}

		this.bindings = bindings;
	}

	/**
	 * Executes a script with the given parameters.
	 * 
	 * @param script
	 *            Script to be executed.
	 * @return TRUE if the script was successfully executed. FALSE if an error
	 *         had occurred.
	 */
	public boolean execute(Script script) {
		// get the right script from the cache.
		final String scriptKey = script.getScriptKey();
		final CompiledScript compScript = compiler.getScript(scriptKey);

		// Probably was not loaded.
		if (compScript == null) {
			return false;
		}

		// Combine custom and std. bindings.
		final Bindings customBindings = script.getBindings();
		customBindings.putAll(bindings);

		// Kinda do a double dispatch in order to customfy script execution if
		// needed.
		return script.execute(customBindings, compScript);
	}

}
