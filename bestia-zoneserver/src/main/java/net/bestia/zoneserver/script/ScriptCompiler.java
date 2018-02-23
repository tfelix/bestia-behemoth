package net.bestia.zoneserver.script;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The ScriptCache is responsible for reading and compiling all the scripts for
 * the system.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class ScriptCompiler {

	private static final Logger LOG = LoggerFactory.getLogger(ScriptCompiler.class);

	private final ScriptEngine engine;

	@Autowired
	public ScriptCompiler(ScriptEngine engine) {

		this.engine = Objects.requireNonNull(engine);
	}

	/**
	 * Tries to compile the given script file.
	 * 
	 * @param file
	 *            The javascript bestia script file.
	 * @return A compiled version of the script or null if there was an error.
	 */
	public CompiledScript compileScript(File file) {
		LOG.trace("Compiling script file: {}.", file);

		try (Reader scriptReader = new FileReader(file)) {

			final CompiledScript script = ((Compilable) engine).compile(scriptReader);
			return script;
			
		} catch (ScriptException | IOException e) {
			LOG.error("Could not compile script.", e);
			return null;
		}
	}
}
