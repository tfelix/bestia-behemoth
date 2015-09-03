package net.bestia.zoneserver.script;

import java.util.HashMap;
import java.util.Map;

public class ExecutionBindings {

	private Map<String, Object> bindings = new HashMap<>();
	
	public void addBinding(String key, Object binding) {
		bindings.put(key, binding);
	}
}
