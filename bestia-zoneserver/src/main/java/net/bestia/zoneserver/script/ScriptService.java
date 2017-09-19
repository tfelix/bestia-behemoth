package net.bestia.zoneserver.script;

import java.util.Objects;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleBindings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.ScriptComponent;
import net.bestia.entity.component.ScriptComponent.Callback;
import net.bestia.messages.internal.entity.EntityComponentMessage;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.zone.ZoneAkkaApi;

/**
 * This class is responsible for fetching the script, creating a appropriate
 * script binding context and then executing the called script.
 * 
 * It also provides the script API so the scripts can interact with the bestia
 * service.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class ScriptService {

	private static final Logger LOG = LoggerFactory.getLogger(ScriptService.class);

	private final EntityService entityService;
	private final ScriptCache scriptCache;
	private final ScriptResolver resolver;

	private ZoneAkkaApi akkaApi;

	@Autowired
	public ScriptService(
			EntityService entityService,
			ZoneAkkaApi akkaApi,
			ScriptCache cache,
			ScriptResolver resolver) {

		this.entityService = Objects.requireNonNull(entityService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
		this.scriptCache = Objects.requireNonNull(cache);
		this.resolver = Objects.requireNonNull(resolver);

	}

	/**
	 * This prepares the script bindings for usage inside this script and
	 * finally calls the given function name.
	 * 
	 * @param script
	 * @param functionName
	 */
	private synchronized void callFunction(CompiledScript script, String functionName) {
		try {

			script.eval(script.getEngine().getContext());
			((Invocable) script.getEngine()).invokeFunction(functionName);

		} catch (NoSuchMethodException e) {
			LOG.error("Error calling script. Script does not contain {}() function.", functionName);
		} catch (ScriptException e) {
			LOG.error("Error during script  {} execution.", e);
		}
	}

	/**
	 * This prepares the script bindings for usage inside this script and
	 * finally calls the given function name.
	 * 
	 * @param script
	 * @param functionName
	 */
	private synchronized void setupScriptBindings(CompiledScript script, ScriptIdent ident, Bindings bindings) {

		final Bindings scriptBindings = script.getEngine().getBindings(ScriptContext.ENGINE_SCOPE);
		scriptBindings.putAll(bindings);
	}

	private CompiledScript resolveScript(ScriptIdent ident) {
		final CompiledScript script = scriptCache.getScript(ident.getType(), ident.getName());

		if (script == null) {
			LOG.warn("Did not find script file: {} ({})", ident.getName(), ident.getType());
			throw new IllegalArgumentException("Could not find script.");
		}

		return script;
	}

	/**
	 * Central entry point for calling a script execution from the Bestia
	 * system. This will fetch the script from cache, if cache does not hold the
	 * script it will attempt to compile it. It will then set the script
	 * environment and execute its main function.
	 * 
	 * @param name
	 *            The name of the script to be called.
	 */

	public void callScript(String name) {
		Objects.requireNonNull(name);
		LOG.debug("Calling script: {}.", name);

		final ScriptIdent ident = resolver.resolveScriptIdent(name);
		final CompiledScript script = resolveScript(ident);

		setupScriptBindings(script, ident, new SimpleBindings());
		callFunction(script, ident.getFunctionName());
	}

	public void callItemScript(String name, Entity source, Entity target) {
		throw new IllegalStateException("Not implemented.");
	}

	public void callItemScript(String name, Entity source, Point target) {
		throw new IllegalStateException("Not implemented.");
	}

	/**
	 * The script callback is triggered via a counter which was initially set
	 * into the {@link ScriptComponent}.
	 * 
	 * @param scriptUuid
	 *            The uuid of the script (an entity can have more then one
	 *            callback script attached).
	 * @param scriptEntityId
	 *            The script entity whose callback is about to be triggered.
	 */
	public void callScriptIntervalCallback(long scriptEntityId, String scriptUuid) {

		LOG.trace("Script {} interval called.", scriptEntityId);

		final ScriptComponent scriptComp = entityService.getComponent(scriptEntityId, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		final String callbackName = scriptComp.getCallbackName(Callback.ON_INTERVAL);

		final ScriptIdent ident = resolver.resolveScriptIdent(callbackName);
		final CompiledScript script = resolveScript(ident);

		// Prepare additional bindings.
		final SimpleBindings bindings = new SimpleBindings();
		bindings.put("SUID", scriptComp.getScriptUuid());

		setupScriptBindings(script, ident, bindings);
		callFunction(script, ident.getFunctionName());
	}

	/**
	 * Starts a recurring script interval attached to an entity. After the given
	 * delay timer in ms the script call will be invoked.
	 * 
	 * @param entity
	 * @param delay
	 * @param callbackFunctionName
	 */
	public void startScriptInterval(Entity entity, int delay, String callbackFunctionName) {
		if (delay <= 0) {
			throw new IllegalArgumentException("Delay must be bigger then 0.");
		}

		Objects.requireNonNull(entity);
		Objects.requireNonNull(callbackFunctionName);

		final ScriptComponent scriptComp = entityService.getComponent(entity, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		scriptComp.setCallbackName(Callback.ON_INTERVAL, callbackFunctionName);
		entityService.updateComponent(scriptComp);

		// Tell the actor which script to periodically call.
		final EntityComponentMessage compMessage = EntityComponentMessage.start(entity.getId(), scriptComp.getId());
		akkaApi.sendEntityActor(entity.getId(), compMessage);
	}

	/**
	 * Stops the running script interval of the given entity.
	 * 
	 * @param entity
	 */
	public void stopScriptInterval(Entity entity) {
		Objects.requireNonNull(entity);

		final ScriptComponent scriptComp = entityService.getComponent(entity, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		scriptComp.removeCallback(Callback.ON_INTERVAL);
		entityService.updateComponent(scriptComp);

		// Tell the actor which script to periodically call.
		final EntityComponentMessage compMessage = EntityComponentMessage.stop(entity.getId(), scriptComp.getId());
		akkaApi.sendEntityActor(entity.getId(), compMessage);
	}
}
