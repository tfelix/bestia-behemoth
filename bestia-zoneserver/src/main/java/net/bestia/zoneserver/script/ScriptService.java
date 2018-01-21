package net.bestia.zoneserver.script;

import java.util.Objects;
import java.util.UUID;

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

	@Autowired
	public ScriptService(
			EntityService entityService,
			ScriptCache cache,
			ScriptResolver resolver) {

		this.entityService = Objects.requireNonNull(entityService);
		this.scriptCache = Objects.requireNonNull(cache);
		this.resolver = Objects.requireNonNull(resolver);

	}

	/**
	 * This prepares the script bindings for usage inside this script and
	 * finally calls the given function name.
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
	 */
	private synchronized void setupScriptBindings(CompiledScript script, ScriptAnchor ident, Bindings bindings) {

		final Bindings scriptBindings = script.getEngine().getBindings(ScriptContext.ENGINE_SCOPE);
		scriptBindings.putAll(bindings);
	}

	private CompiledScript resolveScript(ScriptAnchor ident) {
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

		final ScriptAnchor ident = resolver.resolveScriptIdent(name);
		final CompiledScript script = resolveScript(ident);
		
		//final ScriptFunctionExecutor funcExec = new ScriptFunctionExecutor("main", env, script);

		setupScriptBindings(script, ident, new SimpleBindings());
		callFunction(script, ident.getFunctionName());
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

		final String callbackAnchorString = scriptComp.getCallback(scriptUuid).getScript();
		final ScriptAnchor anchor = ScriptAnchor.fromString(callbackAnchorString);
		final CompiledScript script = resolveScript(anchor);

		setupScriptBindings(script, ident, bindings);
		callFunction(script, ident.getFunctionName());
	}

	/**
	 * Starts a recurring script interval attached to an entity. After the given
	 * delay timer in ms the script call will be invoked. The scripts must be
	 * attached to an entity since this is the only mechanism to allow time
	 * based trigger control of scripts.
	 *
	 */
	public void startScriptInterval(Entity entity, int delay, String callbackFunctionName) {
		if (delay <= 0) {
			throw new IllegalArgumentException("Delay must be bigger then 0.");
		}

		Objects.requireNonNull(entity);
		Objects.requireNonNull(callbackFunctionName);

		final ScriptComponent scriptComp = entityService.getComponentOrCreate(entity, ScriptComponent.class);

		final String scriptUuid = UUID.randomUUID().toString();
		final ScriptAnchor callback = new ScriptComponent.ScriptCallback(scriptUuid, ScriptComponent.TriggerType.ON_INTERVAL);
		scriptComp.addCallback(callback);
		entityService.updateComponent(scriptComp);
	}
}
