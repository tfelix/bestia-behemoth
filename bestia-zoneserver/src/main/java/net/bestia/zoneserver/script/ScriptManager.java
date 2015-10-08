package net.bestia.zoneserver.script;

import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.SimpleBindings;

/**
 * This class loads directories of scripts. And saves them in a compiled form to
 * let them be executed later.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptManager {

	private final Map<String, CompiledScript> cachedScripts = new HashMap<>();
	private Bindings bindings = new SimpleBindings();

	public ScriptManager() {
		// no op.
	}

	/**
	 * Adds a set of bindings which will be used with all triggered scripts.
	 * 
	 * @param bindings
	 *            THe bindings to set as standard bindings.
	 */
	public void setStdBindings(Bindings bindings) {

		if (bindings == null) {
			throw new IllegalArgumentException("Bindings can not be null.");
		}

		this.bindings = bindings;
	}

	/**
	 * Adds new scripts into the manager for later use.
	 * 
	 * @param newCachedScripts
	 *            The new scripts.
	 */
	public void addScripts(Map<String, CompiledScript> newCachedScripts) {
		cachedScripts.putAll(newCachedScripts);
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

		// Probably was not loaded.
		if (!cachedScripts.containsKey(scriptKey)) {
			return false;
		}

		final CompiledScript compScript = cachedScripts.get(scriptKey);

		// Combine custom and std. bindings.
		final Bindings customBindings = script.getBindings();
		customBindings.putAll(bindings);

		// Kinda do a double dispatch in order to customfy script execution if
		// needed.
		return script.execute(customBindings, compScript);
	}

}
