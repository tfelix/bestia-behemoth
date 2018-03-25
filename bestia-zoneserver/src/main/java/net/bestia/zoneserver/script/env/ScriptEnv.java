package net.bestia.zoneserver.script.env;

import net.bestia.zoneserver.script.ScriptApi;

import java.util.Map;
import java.util.Objects;

/**
 * The {@link ScriptEnv} creates and setup an execution environment of each
 * script. The script env should be immutable to ensure thread safety. All the
 * needed variables are setup before execution of the script since each script
 * type in the game has different environment variables which needs to be set.
 *
 * @author Thomas Felix
 *
 */
public abstract class ScriptEnv {

	private final ScriptApi scriptApi;

	public ScriptEnv(ScriptApi scriptApi) {

		this.scriptApi = Objects.requireNonNull(scriptApi);
	}
	
	public void setupEnvironment(Map<String, Object> bindings) {
		bindings.put("BAPI", scriptApi);
		customEnvironmentSetup(bindings);
	}

	/**
	 * Sets the script variables for this execution.
	 */
	abstract protected void customEnvironmentSetup(Map<String, Object> bindings);
}
