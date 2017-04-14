package net.bestia.zoneserver.script;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Map.Entry;

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

	public ScriptCompiler() {

		engine = new ScriptEngineManager().getEngineByName("nashorn");
	}

	public ScriptCompiler(Map<String, Object> globalBindings) {

		engine = new ScriptEngineManager().getEngineByName("nashorn");

		// Attach the global bindings.
		for (Entry<String, Object> entry : globalBindings.entrySet()) {
			LOG.debug("Binding to global script ctx: {} ({})", entry.getKey(),
					entry.getValue().getClass().getSimpleName());
			engine.put(entry.getKey(), entry.getValue());
		}
	}

	public CompiledScript compiledScript(File file) {

		try (Reader scriptReader = new FileReader(file)) {
			final CompiledScript script = ((Compilable) engine).compile(scriptReader);
			return script;
		} catch (ScriptException | IOException e) {
			LOG.error("Could not compile script.", e);
			return null;
		}
	}
}
