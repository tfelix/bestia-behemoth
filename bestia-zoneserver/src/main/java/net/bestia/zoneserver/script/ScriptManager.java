package net.bestia.zoneserver.script;

import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class loads directories of scripts. And saves them in a compiled form to let them be executed later.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptManager {

	private final static Logger log = LogManager.getLogger(ScriptManager.class);

	private class ScriptPackage {

		public final Bindings bindings;
		public final ScriptCache cache;

		public ScriptPackage(ScriptCache cache, Bindings bindings) {
			this.bindings = bindings;
			this.cache = cache;
		}
	}

	private Map<String, ScriptPackage> scriptPackages = new HashMap<>();

	/**
	 * Adds a script to the manager. The script consists of the key determining the kind of the script (item, attack
	 * etc.) a cache containing all the scripts and the permanent bindings for this kind of scripts. These will be added
	 * upon each invocation.
	 * 
	 * @param scriptKey
	 *            Script kind (item, attack etc.)
	 * @param cache
	 *            The script cache.
	 * @param bindings
	 *            The permanent bindings of this script.
	 */
	public void addCache(String scriptKey, ScriptCache cache, Bindings bindings) {

		final ScriptPackage pkg = new ScriptPackage(cache, bindings);

		scriptPackages.put(scriptKey, pkg);
	}

	/**
	 * Executes a script with the given parameters.
	 * 
	 * @param script
	 *            Script to be executed.
	 */
	public void executeScript(Script script) {

		final String scriptKey = script.getScriptKey();
		final String scriptName = script.getName();

		final ScriptPackage pkg = scriptPackages.get(scriptKey);

		if (pkg == null) {
			log.error("Scriptpackage with key {} was not found.", scriptKey);
			return;
		}

		CompiledScript compiledScript = pkg.cache.getScript(scriptName);

		if (compiledScript == null) {
			log.error("Script with key {} and name {} was not found.", scriptKey, scriptName);
			return;
		}

		// Prepare bindings.
		Bindings scriptBindings = script.getBindings();

		scriptBindings.putAll(pkg.bindings);

		try {
			compiledScript.eval(scriptBindings);
		} catch (ScriptException e) {
			log.error("Error while executing script: {}.{}", scriptKey, scriptName, e);
		}
	}

}
