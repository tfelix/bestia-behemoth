package net.bestia.zoneserver.script;

import javax.script.SimpleBindings;

public interface Script {

	/**
	 * Get all the bindings for this script.
	 * 
	 * @return
	 */
	SimpleBindings getBindings();

	String getScriptKey();
}
