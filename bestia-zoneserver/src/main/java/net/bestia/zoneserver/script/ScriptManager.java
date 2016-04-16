package net.bestia.zoneserver.script;

import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.SimpleBindings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class loads directories of scripts. And saves them in a compiled form to
 * let them be executed later.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptManager {
	
	private final Logger LOG = LogManager.getLogger(ScriptManager.class);
	
	/**
	 * Facade providing scripts with access to our logger.
	 *
	 */
	@SuppressWarnings("unused")
	private class LogFacade {
		
		private final Logger LOG = LogManager.getLogger("ScriptLogger");
		
		
		public void info(String s) {
			LOG.info(s);
		}
		
		public void debug(String s) {
			LOG.debug(s);
		}
	}

	private final Map<String, CompiledScript> cachedScripts = new HashMap<>();
	private Bindings bindings = new SimpleBindings();

	public ScriptManager() {
		
		// Add a few bindings.
		bindings.put("log", new LogFacade());
	}

	/**
	 * Adds a set of bindings which will be used with all triggered scripts.
	 * 
	 * @param bindings
	 *            THe bindings to set as standard bindings.
	 */
	public void setStandardBindings(Bindings bindings) {

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
		return execute(script, null);
	}

	/**
	 * Executes a script with the given parameters.
	 * 
	 * @param script
	 *            Script to be executed.
	 * @param localBindings
	 *            Local bindings will overwrite all other bindings. The caller
	 *            can setup new bindings with this method.
	 * @return TRUE if the script was successfully executed. FALSE if an error
	 *         had occurred.
	 */
	public boolean execute(Script script, Bindings localBindings) {
		// get the right script from the cache.
		final String scriptKey = script.getScriptKey();

		// Probably was not loaded.
		if (!cachedScripts.containsKey(scriptKey)) {
			LOG.warn("Script {} was not found.", scriptKey);
			return false;
		}

		final CompiledScript compScript = cachedScripts.get(scriptKey);

		// Combine custom and std. bindings.
		final Bindings customBindings = script.getBindings();
		customBindings.putAll(bindings);
		
		if(localBindings != null) {
			customBindings.putAll(localBindings);
		}

		// Kinda do a double dispatch in order to customfy script execution if
		// needed.
		return script.execute(customBindings, compScript);
	}

}
