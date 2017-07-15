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
import net.bestia.messages.internal.script.ScriptIntervalMessage;
import net.bestia.model.geometry.Point;
import net.bestia.zoneserver.actor.ZoneAkkaApi;

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

	private static class ScriptIdent {
		public ScriptType type;
		public String name;
		public String functionName;

		public ScriptIdent() {

		}
	}

	private static final Logger LOG = LoggerFactory.getLogger(ScriptService.class);

	private static final String MAIN_FUNC = "main";

	private final EntityService entityService;
	private final ScriptCache scriptCache;

	private ZoneAkkaApi akkaApi;

	@Autowired
	public ScriptService(
			EntityService entityService,
			ZoneAkkaApi akkaApi,
			ScriptCache cache) {

		this.entityService = Objects.requireNonNull(entityService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
		this.scriptCache = Objects.requireNonNull(cache);

	}

	/**
	 * To call into random scripts the name can be encoded like the following
	 * scheme:
	 * 
	 * <pre>
	 * test - This will call the function test() in the original script file.
	 * item/apple:test - This will call the function test in the script file item/apple.js
	 * item/apple - This will call the default main function in script file item/apple.js
	 * item/apple.js - Same as above.
	 * </pre>
	 * 
	 * @param callback
	 * @return
	 */
	private ScriptIdent resolveCallbackName(String callbackName) {

		String[] token = callbackName.split(":");
		String funcName;

		if (token.length == 2) {
			funcName = token[1];
		} else {
			funcName = MAIN_FUNC;
		}

		String scriptName = token[0];
		if (scriptName.endsWith(".js")) {
			scriptName = scriptName.replace(".js", "");
		}

		if (scriptName.startsWith("/")) {
			scriptName = scriptName.substring(1).toUpperCase();
		}

		// Detect the type.
		ScriptType type;
		if (scriptName.startsWith("ITEM")) {
			type = ScriptType.ITEM;
		} else if (scriptName.startsWith("ATTACK")) {
			type = ScriptType.ATTACK;
		} else if (scriptName.startsWith("STATUS_EFFECT")) {
			type = ScriptType.STATUS_EFFECT;
		} else {
			type = ScriptType.NONE;
		}

		final ScriptIdent ident = new ScriptIdent();

		ident.name = callbackName;
		ident.type = type;
		ident.functionName = funcName;

		return ident;
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

		scriptBindings.put("SCRIPT", ident.name);
		scriptBindings.putAll(bindings);
	}

	private CompiledScript resolveScript(ScriptIdent ident) {
		final CompiledScript script = scriptCache.getScript(ident.type, ident.name);

		if (script == null) {
			LOG.warn("Did not find script file: {} ({})", ident.name, ident.type);
			throw new IllegalArgumentException("Could not find script.");
		}

		return script;
	}

	/**
	 * Central entry point for calling a script execution from the bestia
	 * system. This will fetch the script from cache, if cache does not hold the
	 * script it will attempt to compile it. It will then set the script
	 * environment and execute its main function.
	 * 
	 * @param name
	 * @param type
	 */

	public void callScript(String name) {
		final ScriptIdent ident = resolveCallbackName(name);
		final CompiledScript script = resolveScript(ident);

		setupScriptBindings(script, ident, new SimpleBindings());
		callFunction(script, ident.functionName);
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
	 * @param scriptEntityId
	 *            The script entity whose callback is about to be triggered.
	 */
	public void callScriptIntervalCallback(long scriptEntityId) {

		LOG.trace("Script {} interval called.", scriptEntityId);

		final ScriptComponent scriptComp = entityService.getComponent(scriptEntityId, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		final String callbackName = scriptComp.getOnIntervalCallbackName();
		
		final ScriptIdent ident = resolveCallbackName(callbackName);
		final CompiledScript script = resolveScript(ident);
		
		// Prepare additional bindings.
		final SimpleBindings bindings = new SimpleBindings();
		bindings.put("SCRIPT_ID", scriptComp.getScriptUUID());
		
		setupScriptBindings(script, ident, bindings);
		callFunction(script, ident.functionName);
	}

	/**
	 * Starts a recurring script interval attached to an entity. After the given
	 * delay timer in ms the script call will be invoced.
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

		scriptComp.setOnIntervalCallbackName(callbackFunctionName);
		entityService.saveComponent(scriptComp);

		// Tell the actor which script to periodically call.
		final ScriptIntervalMessage message = new ScriptIntervalMessage(entity.getId(), delay);
		akkaApi.sendEntityActor(entity.getId(), message);
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

		scriptComp.setOnIntervalCallbackName(null);
		entityService.saveComponent(scriptComp);
	}
}
