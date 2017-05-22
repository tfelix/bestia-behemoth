package net.bestia.zoneserver.script;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.model.geometry.CollisionShape;
import net.bestia.zoneserver.entity.Entity;

/**
 * This is a wrapper class for a entity to be used inside scripts. It holds
 * various shortcut methods which will call different services and serves as a
 * shortcut to interact with these services. It is basically a facade wrapper
 * for the native entities which are used as scripts.
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
		LOG.trace("Entity: {}. Set interval function callback name: {}.", entity, callbackFunctionName);

		scriptService.startScriptInterval(entity, delay, callbackFunctionName);

		return this;
	}

	public ScriptEntityWrapper setVisual() {
		LOG.trace("Entity: {}. Set visual: {}", entity);

		return this;
	}

	public ScriptEntityWrapper playAnimation(String name) {
		LOG.trace("Entity: {}. Play animation: {}", name);

		return this;
	}

	public ScriptEntityWrapper setLivetime(int duration) {
		LOG.trace("Entity: {}. Sets lifetime: {} ms.", entity, duration);

		return this;
	}

	public ScriptEntityWrapper setPosition(long x, long y) {
		LOG.trace("Entity: {}. Sets position x: {} y: {}.", entity, x, y);

		return this;
	}

	public ScriptEntityWrapper setShape(CollisionShape shape) {
		LOG.trace("Entity: {}. Sets shape: {}.", entity, shape);

		return this;
	}

	public ScriptEntityWrapper setOnEnter(String callbackName) {
		return this;
	}

	public ScriptEntityWrapper setOnLeave(String callbackName) {
		return this;
	}
}
