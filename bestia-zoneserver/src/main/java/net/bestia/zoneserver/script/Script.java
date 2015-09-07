package net.bestia.zoneserver.script;

import javax.script.Bindings;
import javax.script.SimpleBindings;

/**
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class Script {

	private final String name;
	private final Bindings bindings = new SimpleBindings();

	public Script(String name) {
		if (name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Name can not be null or empty.");
		}

		this.name = name;
	}

	/**
	 * Must return the script key (type of the script: item, attack etc.)
	 * 
	 * @return The type of the script.
	 */
	public abstract String getScriptKey();

	public String getName() {
		return name;
	}
	
	protected void addBinding(String name, Object obj) {
		bindings.put(name, obj);
	}

	/**
	 * Returns the temporary bindings of the script context to be executed.
	 * 
	 * @return The temporary script bindings.
	 */
	public Bindings getBindings() {
		return bindings;
	}

}