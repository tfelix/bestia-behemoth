package net.bestia.zoneserver.script;

import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.CompiledScript;

import org.codehaus.groovy.jsr223.GroovyCompiledScript;

/**
 * This class loads directories of scripts. And saves them in a compiled form to
 * let them be executed later.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptManager {

	private Map<String, GroovyCompiledScript> compiledScripts = new HashMap<>();
	private Bindings bindings;


	public ScriptManager() {
		// no op.
	}

	public void setStdBindings(Bindings bindings) {

		if (bindings == null) {
			throw new IllegalArgumentException("Bindings can not be null.");
		}

		this.bindings = bindings;
	}

	/**
	 * Load all the script required for the system.
	 */
	public void load() {

	}

	/**
	 * Executes a script with the given parameters.
	 * 
	 * @param script
	 *            Script to be executed.
	 */
	public boolean execute(Script script) {
		// get the right script from the cache.
		final String scriptKey = script.getScriptKey();
		final CompiledScript compScript = compiledScripts.get(scriptKey);

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
