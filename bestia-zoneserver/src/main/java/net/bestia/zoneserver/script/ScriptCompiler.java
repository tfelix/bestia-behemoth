package net.bestia.zoneserver.script;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
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
public class ScriptCompiler {

	private static final Logger log = LogManager.getLogger(ScriptCompiler.class);

	private final Map<String, CompiledScript> compiledScripts = new HashMap<String, CompiledScript>();

	// move this into initialization part so that you do not call this every time.
	private final ScriptEngineManager manager = new ScriptEngineManager();
	private final ScriptEngine engine = manager.getEngineByName("groovy");

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

				log.debug("Compiling script: {}", child.getCanonicalPath());

				try {
					compile(child);
				} catch (ScriptException e) {
					log.error("Could not parse script: {}", child.getCanonicalPath(), e);
				}
			}
		}
	}

	private void compile(File scriptFile) throws ScriptException, IOException {
		if (engine == null) {
			throw new ScriptException("Can not create script engine.");
		}

		final Reader scriptReader = new FileReader(scriptFile);
		final CompiledScript script = ((Compilable) engine).compile(scriptReader);

		// Script name.
		final String scriptFileName = FilenameUtils.removeExtension(scriptFile.getName());

		compiledScripts.put(scriptFileName, script);
	}

	public CompiledScript getScript(String name) {
		return compiledScripts.get(name);
	}
}
