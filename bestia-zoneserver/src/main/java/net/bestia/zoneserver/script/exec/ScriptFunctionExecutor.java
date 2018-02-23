package net.bestia.zoneserver.script.exec;

import java.util.Objects;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.zoneserver.script.env.ScriptEnv;

/**
 * Runs a certain javascript function.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptFunctionExecutor implements Executer {

	private final static Logger LOG = LoggerFactory.getLogger(ScriptFunctionExecutor.class);

	private final CompiledScript script;
	private final ScriptEnv env;
	private final String fnName;

	public ScriptFunctionExecutor(String fnName, ScriptEnv env, CompiledScript script) {

		this.fnName = fnName;
		this.script = Objects.requireNonNull(script);
		this.env = Objects.requireNonNull(env);

	}

	@Override
	public void execute() {
		final ScriptEngine engine = script.getEngine();

		// Setup the script environment.
		final Bindings bindings = engine.createBindings();
		env.setupEnvironment(bindings);

		engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

		final Invocable inv = (Invocable) script.getEngine();

		try {
			inv.invokeFunction(fnName);
		} catch (NoSuchMethodException e) {
			LOG.error("Function {} is missing in script.", fnName, e);
		} catch (ScriptException e) {
			LOG.error("Error during script execution.", e);
		}
	}
}
