package net.bestia.zoneserver.script;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The ScriptCache is responsible for reading and compiling all the scripts for the system.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptCache {
	
	private static final Logger log = LogManager.getLogger(ScriptCache.class);

	private Map<String, CompiledScript> compiledScripts = new HashMap<String, CompiledScript>();

	/**
	 * Loads all scripts inside this folder and caches
	 * 
	 * @param scriptFolder
	 */
	public void load(File scriptFolder) throws IOException {
		if (!scriptFolder.isDirectory()) {
			throw new IOException("Path is no directory of scriptfiles: " + scriptFolder.toString());
		}

		File[] directoryListing = scriptFolder.listFiles();
		if (directoryListing != null) {
			for (File child : directoryListing) {
				try {
					compile(child);
				} catch (ScriptException e) {
					log.error("Could not parse script: {}", child.getAbsolutePath());
				}
			}
		}
	}

	private void compile(File scriptFile) throws ScriptException {
		// move this into initialization part so that you do not call this every time.
		final ScriptEngineManager manager = new ScriptEngineManager();
		final ScriptEngine engine = manager.getEngineByName("groovy");
		final CompiledScript script = ((Compilable) engine).compile(scriptFile.getAbsolutePath());

		// Script name.
		final String scriptFileName = FilenameUtils.removeExtension(scriptFile.getName());

		compiledScripts.put(scriptFileName, script);

		// the code below will use the precompiled script code
		//Bindings bindings = new SimpleBindings();
		/*
		 * bindings.put("state", state; bindings.put("zipcode", zip); bindings.put("url",
		 * locationAwareAd.getLocationData().getGeneratedUrl()); url = script.eval(bindings);
		 */
	}
	
	public CompiledScript getScript(String name) {
		return compiledScripts.get(name);
	}
}
