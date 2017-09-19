package net.bestia.zoneserver.script;

import java.util.Map;

/**
 * The {@link ScriptEnv} creates and setup an execution environment of each
 * script. The script env should be immutable to ensure thread safety. All the
 * needed variables are setup before execution of the script since each script
 * type in the game has different environment variables which needs to be set.
 * 
 * @author Thomas Felix
 *
 */
public interface ScriptEnv {
	
	/**
	 * Sets the script variables for this execution.
	 */
	void setupEnvironment(Map<String, Object> bindings);

	//void executeScript();
}
