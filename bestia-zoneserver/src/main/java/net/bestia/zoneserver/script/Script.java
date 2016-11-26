package net.bestia.zoneserver.script;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Script {

	/**
	 * Get all the bindings for this script.
	 * 
	 * @return
	 */
	SimpleBindings getBindings();

	String getScriptKey();
}
