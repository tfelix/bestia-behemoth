package net.bestia.zoneserver.script;

import java.util.Objects;

import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import net.bestia.messages.internal.ScriptIntervalMessage;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.actor.script.PeriodicScriptRunnerActor;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.components.ScriptComponent;

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

	@Autowired
	public ScriptService(EntityService entityService, ZoneAkkaApi akkaApi, ScriptCache cache) {

		this.entityService = Objects.requireNonNull(entityService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
		this.scriptCache = Objects.requireNonNull(cache);
	}

	/**
	 * This prepares the script bindings for usage inside this script and
	 * finally calls the given function name.
	 * 
	 * @param script
	 * @param functionName
	 */
	private void setupScriptAndCallFunction(CompiledScript script, String functionName) {

	}

	/**
	 * Removes a script entity completely from the system. It takes care of
	 * removing all of the entity components as well as saved script variable
	 * data.
	 * 
	 * @param scriptEntityId
	 *            The entity id of the script.
	 */
	public void deleteScriptEntity(long scriptEntityId) {

		final Entity scriptEntity = entityService.getEntity(scriptEntityId);

		if (!entityService.hasComponent(scriptEntity, ScriptComponent.class)) {
			return;
		}
		
		stopScriptInterval(scriptEntity);
		entityService.delete(scriptEntity);

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
	public synchronized void callScript(String name, ScriptType type) {
		LOG.debug("Executing script: {} ({}).", name, type);

		final CompiledScript script = scriptCache.getScript(type, name);

		try {
			((Invocable) script.getEngine()).invokeFunction(MAIN_FUNC);
		} catch (NoSuchMethodException e) {
			LOG.error("Error calling script. Script {} ({}) does not contain main() function.", name, type);
		} catch (ScriptException e) {
			LOG.error("Error during script  {} ({})  execution.", name, type, e);
		}
	}

	public void triggerScriptIntervalCallback(long scriptEntityId) {

		LOG.trace("Script {} interval called.", scriptEntityId);

		final ScriptComponent scriptComp = entityService.getComponent(scriptEntityId, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		final String callbackName = scriptComp.getOnIntervalCallbackName();
		final String scriptName = scriptComp.getScriptName();
		final ScriptType type = scriptComp.getScriptType();

		final CompiledScript script = scriptCache.getScript(type, scriptName);

		try {

			LOG.trace("Calling script function: {} from script: {} ({}).", callbackName, scriptName, type);
			((Invocable) script.getEngine()).invokeFunction(callbackName);

		} catch (NoSuchMethodException | ScriptException e) {
			LOG.error("Error while executing script interval callback ({} type: {})", scriptName, type);
			deleteScriptEntity(scriptEntityId);
		}
	}

	public void startScriptInterval(Entity entity, int delay, String callbackFunctionName) {
		final ScriptComponent scriptComp = entityService.getComponent(entity, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		if (delay <= 0) {
			throw new IllegalArgumentException("Delay must be bigger then 0.");
		}

		final ActorRef scriptRunner = akkaApi.startUnnamedActor(PeriodicScriptRunnerActor.class);

		scriptComp.setScriptActorPath(scriptRunner.path());
		scriptComp.setOnIntervalCallbackName(callbackFunctionName);
		entityService.saveComponent(scriptComp);

		// Tell the actor which script to periodically call.
		final ScriptIntervalMessage message = new ScriptIntervalMessage(entity.getId(), delay);
		scriptRunner.tell(message, ActorRef.noSender());
	}

	public void stopScriptInterval(Entity entity) {
		final ScriptComponent scriptComp = entityService.getComponent(entity, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		final ActorPath callbackActorPath = scriptComp.getScriptActorPath();

		if (callbackActorPath == null) {
			return;
		}

		akkaApi.sendToActor(callbackActorPath, PoisonPill.getInstance());

		scriptComp.setScriptActorPath(null);
		scriptComp.setOnIntervalCallbackName(null);
		entityService.saveComponent(scriptComp);
	}
}
