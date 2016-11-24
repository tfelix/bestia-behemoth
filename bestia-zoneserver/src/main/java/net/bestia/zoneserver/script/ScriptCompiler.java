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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ScriptCache is responsible for reading and compiling all the scripts for
 * the system.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptCompiler {

	private static final Logger LOG = LoggerFactory.getLogger(ScriptCompiler.class);

	private final ScriptEngine engine;
	private final Map<String, CompiledScript> compiledScripts = new HashMap<String, CompiledScript>();

	/**
	 * Returns a unmodifiable map of all loaded and compiled scripts archived
	 * under the given script key.
	 * 
	 * @return Map with the SCRIPTKEY and {@link CompiledScript}.
	 */
	public CompiledScript getCompiledScripts(String key) {
		return compiledScripts.get(key);
	}

	public ScriptCompiler() {

		engine = new ScriptEngineManager().getEngineByName("nashorn");
	}

	/**
	 * Loads a script with a given script key. The key is important because it
	 * must be remembered to later retrieve the compiled script.
	 * 
	 * @param scriptKey
	 * @param scriptFile
	 * @throws IOException
	 */
	public void load(String scriptKey, File scriptFile) throws IOException {

		if (engine == null) {
			throw new IOException("Can not create script engine.");
		}

		if (scriptKey == null || scriptKey.isEmpty()) {
			throw new IllegalArgumentException("ScriptKey can not be null or empty.");
		}

		if (scriptFile == null) {
			throw new IllegalArgumentException("ScriptFile can not be null.");
		}

		LOG.debug("Compiling script: {}", scriptFile.getCanonicalPath());

		final Reader scriptReader = new FileReader(scriptFile);

		try {
			final CompiledScript script = ((Compilable) engine).compile(scriptReader);
			compiledScripts.put(scriptKey, script);
		} catch (ScriptException e) {
			LOG.error("Could not compile script: {}", scriptFile.getCanonicalPath(), e);
		} finally {
			scriptReader.close();
		}
	}
}
