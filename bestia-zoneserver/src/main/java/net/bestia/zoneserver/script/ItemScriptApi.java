package net.bestia.zoneserver.script;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * This is basically a facade which hides all the underlying services from the
 * script. All the methods inside this facade are exposed towards the called
 * script and can be used via javascript.
 * 
 * @author Thomas Felix
 *
 */
public class ItemScriptApi {

	/**
	 * Creates the bindings for this class.
	 * 
	 * @return
	 */
	public Map<String, Object> getBindings() {
		
		Map<String, Object> scope = new HashMap<>();
		
		for(Method m : this.getClass().getMethods()) {
			
			// Add methods to bindings.
			
		}
		
		return scope;

	}

}
