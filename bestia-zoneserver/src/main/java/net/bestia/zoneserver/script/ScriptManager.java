package net.bestia.zoneserver.script;

import java.util.HashMap;
import java.util.Map;

/**
 * This class loads directories of scripts. And saves them in a compiled form to let them be executed later.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class ScriptManager {
	
	private class ScriptPackage {
		
		public final ExecutionBindings bindings;
		public final ScriptCache cache;
		
		public ScriptPackage(ScriptCache cache, ExecutionBindings bindings) {
			this.bindings = bindings;
			this.cache = cache;
		}
	}
	
	private Map<String, ScriptPackage> scriptPackages = new HashMap<>();
	
	public void addCache(String scriptKey, ScriptCache cache, ExecutionBindings bindings) {		
		
		final ScriptPackage pkg = new ScriptPackage(cache, bindings);	
		
		scriptPackages.put(scriptKey, pkg);				
	}

}
