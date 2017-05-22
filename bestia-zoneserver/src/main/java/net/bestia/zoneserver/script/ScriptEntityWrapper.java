package net.bestia.zoneserver.script;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.zoneserver.entity.Entity;

/**
 * This is a wrapper class for a entity to be used inside scripts. It holds
 * various shortcut methods which will call different services and serves as a
 * shortcut to interact with these services. It is basically a facade wrapper
 * for the native bestia entity which is of no big use.
 * 
 * @author Thomas Felix
 *
 */
public class ScriptEntityWrapper {

	private static final Logger LOG = LoggerFactory.getLogger(ScriptEntityWrapper.class);

	private final Entity entity;
	private final ScriptService scriptService;

	public ScriptEntityWrapper(Entity entity, ScriptService scriptService) {

		this.entity = Objects.requireNonNull(entity);
		this.scriptService = Objects.requireNonNull(scriptService);
	}

	public ScriptEntityWrapper setOnTouch(Runnable callback) {
		LOG.trace("Script Entity: {}. setOnTouch called.", entity);

		return this;
	}

	/**
	 * Defines a callback function which gets attached to the script and is
	 * periodically called.
	 * 
	 * @param delay
	 * @param callback
	 * @return Returns the object to make the call chainable.
	 */
	public ScriptEntityWrapper setInterval(int delay, String callbackFunctionName) {
		LOG.trace("Script Entity: {}. setIntervalFunction {} called.", entity, callbackFunctionName);

		scriptService.startScriptInterval(entity, delay, callbackFunctionName);

		return this;
	}
	
	public ScriptEntityWrapper setVisual() {
		return this;
	}
	
	public ScriptEntityWrapper playAnimation(String name) {
		return this;
	}
	
	public ScriptEntityWrapper setLivetime(int duration) {
		return this;
	}
	
	public ScriptEntityWrapper setPosition(long x, long y) {
		return this;
	}
	
	public ScriptEntityWrapper setShape() {
		return this;
	}
	
	public ScriptEntityWrapper setOnEnter(String callbackName) {
		return this;
	}
	
	public ScriptEntityWrapper setOnLeave(String callbackName) {
		return this;
	}
}
