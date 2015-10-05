package net.bestia.zoneserver.script;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public abstract class Script2 {
	
	private final static Logger LOG = LogManager.getLogger(Script2.class);
	
	private final String name;
	private final Bindings bindings = new SimpleBindings();

	public Script2() {
		name = "STD-CTOR";
	}
	
	public Script2(String name) {
		if(name == null || name.isEmpty()) {
			throw new IllegalArgumentException("Name can not be null or empty.");
		}
		this.name = name;
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
	
	public abstract String getScriptSubPath();
	
	abstract String getScriptKey();

	boolean execute(Bindings bindings, CompiledScript compScript) {
		try {
			compScript.eval(bindings);
		} catch (ScriptException e) {
			LOG.error("Could not execute script: {}", getName(), e);
			return false;
		}
		return true;
	}
}
