package net.bestia.zoneserver.script;

import java.util.Objects;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Script {

	private final static Logger LOG = LogManager.getLogger(Script.class);

	public static final String SCRIPT_PREFIX_ITEM = "item.";
	public static final String SCRIPT_PREFIX_ATTACK = "attack.";
	public static final String SCRIPT_PREFIX_MAP = "map.";

	private final String name;
	private final String prefix;
	
	private final Bindings bindings = new SimpleBindings();

	/**
	 * Creates a new script with the bindings defined by the script builder.
	 * 
	 * @param scriptBuilder
	 */
	Script(ScriptBuilder builder) {

		name = builder.name;	
		if(name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Name can not be null or empty.");
		}
		
		prefix = builder.scriptPrefix;
		
		addBinding("api", Objects.requireNonNull(builder.api, "Script API can not be null."));
		
		addBinding("targetX", builder.x);
		addBinding("targetY", builder.y);
		addBinding("targetEntity", builder.target);
		
		addBinding("owner", builder.owner);
		addBinding("inventory", builder.inventory);
		
		addBinding("scriptName", name);

	}

	public String getName() {
		return name;
	}

	/**
	 * This will add a temporary binding to the script which lasts only during
	 * the execution.
	 * 
	 * @param name
	 *            Name of the binding.
	 * @param obj
	 *            Data to bind to this name.
	 */
	private void addBinding(String name, Object obj) {
		bindings.put(name, obj);
	}

	boolean execute(Bindings externalBindings, CompiledScript compScript) {
		try {
			
			// Combine custom and std. bindings.
			bindings.putAll(externalBindings);			
			compScript.eval(bindings);
			
		} catch (ScriptException e) {
			LOG.error("Could not execute script: {}", getName(), e);
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return String.format("Script[name: %s]", getName());
	}

	public String getPrefix() {
		return prefix;
	}
}
