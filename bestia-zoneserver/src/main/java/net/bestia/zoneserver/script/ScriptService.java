package net.bestia.zoneserver.script;

import java.util.Objects;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import akka.actor.ActorRef;
import net.bestia.messages.internal.script.ScriptIntervalMessage;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.actor.script.PeriodicScriptRunnerActor;
import net.bestia.zoneserver.configuration.StaticConfigurationService;
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
	private static final String MAIN_FUNC = "main";

	private final EntityService entityService;
	private final ZoneAkkaApi akkaApi;
	private final ScriptCache scriptCache;
	private final StaticConfigurationService configService;
	private final ScriptApi scriptApi;

	@Autowired
	public ScriptService(
			EntityService entityService,
			ZoneAkkaApi akkaApi,
			ScriptCache cache,
			ScriptApi scriptApi,
			StaticConfigurationService configService) {

		this.entityService = Objects.requireNonNull(entityService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
		this.scriptCache = Objects.requireNonNull(cache);
		this.configService = Objects.requireNonNull(configService);
		this.scriptApi = Objects.requireNonNull(scriptApi);
	}

	/**
	 * This prepares the script bindings for usage inside this script and
	 * finally calls the given function name.
	 * 
	 * @param script
	 * @param functionName
	 */
	private synchronized void setupScriptAndCallFunction(CompiledScript script, String name, ScriptType type,
			String functionName) {

		final Bindings scriptBindings = script.getEngine().getBindings(ScriptContext.ENGINE_SCOPE);
		scriptBindings.put("SERVER_VERSION", configService.getServerVersion());
		scriptBindings.put("Bestia", scriptApi);
		scriptBindings.put("MYSCRIPT", name);
		scriptBindings.put("MYTYPE", type);

		try {

			script.eval(script.getEngine().getContext());
			((Invocable) script.getEngine()).invokeFunction(functionName);

		} catch (NoSuchMethodException e) {
			LOG.error("Error calling script. Script {} ({}) does not contain {}() function.", name, type, functionName);
		} catch (ScriptException e) {
			LOG.error("Error during script  {} ({})  execution.", name, type, e);
		}
	}

	/**
	 * Alias for {@link #freeScriptComponent(Entity)}. Entity is resolved first.
	 * 
	 * @param scriptEntityId
	 *            The ID of the script entity.
	 * @return The removed {@link ScriptComponent} or null if no component was
	 *         attached to the entity.
	 */
	public ScriptComponent freeScriptComponent(long scriptEntityId) {

		final Entity scriptEntity = entityService.getEntity(scriptEntityId);
		return freeScriptComponent(scriptEntity);

	}

	/**
	 * Removes a script component from an entity.It takes care of releasing all
	 * script related resources to the attached script component. The detached
	 * and cleared script components is returned and can be reused. If the
	 * entity contains no {@link ScriptComponent} null is returned.
	 * 
	 * @param scriptEntityId
	 *            The entity id of the script.
	 * @return The removed {@link ScriptComponent} or null if no component was
	 *         attached to the entity.
	 */
	public ScriptComponent freeScriptComponent(Entity scriptEntity) {
		if (!entityService.hasComponent(scriptEntity, ScriptComponent.class)) {
			return null;
		}

		stopScriptInterval(scriptEntity);

		final ScriptComponent scriptComp = entityService.getComponent(scriptEntity, ScriptComponent.class).get();
		entityService.deleteComponent(scriptEntity, scriptComp);

		scriptComp.setEntityId(0);

		return scriptComp;
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
	public void callScript(String name, ScriptType type) {
		LOG.debug("Executing script: {} ({}).", name, type);

		final CompiledScript script = scriptCache.getScript(type, name);
		
		if(script == null) {
			LOG.warn("Did not find script file: {} ({})", name, type);
			return;
		}

		setupScriptAndCallFunction(script, name, type, MAIN_FUNC);
	}

	public void callAttackScript(String name) {
		throw new IllegalStateException("Not implements");
	}

	/**
	 * The script callback is triggered via a counter which was initially set
	 * into the {@link ScriptComponent}.
	 * 
	 * @param scriptEntityId
	 *            The script entity whose callback is about to be triggered.
	 */
	public void triggerScriptIntervalCallback(long scriptEntityId) {

		LOG.trace("Script {} interval called.", scriptEntityId);

		final ScriptComponent scriptComp = entityService.getComponent(scriptEntityId, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		final String callbackName = scriptComp.getOnIntervalCallbackName();
		final String scriptName = scriptComp.getScriptName();
		final ScriptType type = scriptComp.getScriptType();

		final CompiledScript script = scriptCache.getScript(type, scriptName);

		setupScriptAndCallFunction(script, scriptName, type, callbackName);
	}

	public void startScriptInterval(Entity entity, int delay, String callbackFunctionName) {
		if (delay <= 0) {
			throw new IllegalArgumentException("Delay must be bigger then 0.");
		}
		
		Objects.requireNonNull(callbackFunctionName);

		final ScriptComponent scriptComp = entityService.getComponent(entity, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);
		
		scriptComp.setOnIntervalCallbackName(callbackFunctionName);
		entityService.saveComponent(scriptComp);

		final ActorRef scriptRunner = akkaApi.startUnnamedActor(PeriodicScriptRunnerActor.class);

		// Tell the actor which script to periodically call.
		final ScriptIntervalMessage message = new ScriptIntervalMessage(entity.getId(), delay);
		scriptRunner.tell(message, ActorRef.noSender());
	}

	public void stopScriptInterval(Entity entity) {
		final ScriptComponent scriptComp = entityService.getComponent(entity, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		scriptComp.setOnIntervalCallbackName(null);
		entityService.saveComponent(scriptComp);
	}
}
