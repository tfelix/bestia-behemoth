package net.bestia.zoneserver.script;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Objects;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import net.bestia.messages.internal.ScriptIntervalMessage;
import net.bestia.model.geometry.CollisionShape;
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

	private final EntityService entityService;
	private final ZoneAkkaApi akkaApi;
	private final ScriptApi scriptApi;

	@Autowired
	public ScriptService(EntityService entityService, ZoneAkkaApi akkaApi, ScriptApi scriptApi) {

		this.entityService = Objects.requireNonNull(entityService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
		this.scriptApi = Objects.requireNonNull(scriptApi);
	}

	public void saveData(String scriptUid, String json) {

		LOG.debug("Saving script data for script: {} data: {}.", scriptUid, json);
	}

	public void loadData(String scriptUid) {

		LOG.debug("Loading script data for script: {} data: {}.", scriptUid, "nothing");
	}

	/**
	 * Central entry point for calling a script execution from the bestia system.
	 * This will fetch the script from cache, if cache does not hold the script
	 * it will attempt to compile it. It will then set the script environment
	 * and execute its main function.
	 * 
	 * @param name
	 * @param type
	 */
	public void callScript(String name, ScriptType type) {

	}

	public void triggerScriptIntervalCallback(long scriptEntityId) {

		LOG.trace("Script {} interval called.", scriptEntityId);

		final ScriptComponent scriptComp = entityService.getComponent(scriptEntityId, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		final String callbackName = scriptComp.getOnIntervalCallbackName();

		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("nashorn");
		engine.put("Bestia", scriptApi);

		ClassLoader classLoader = getClass().getClassLoader();
		File testFile = new File(classLoader.getResource("script/attack/create_aoe_dmg.js").getFile());
		try {
			CompiledScript compiled = ((Compilable) engine).compile(new FileReader(testFile));
			compiled.eval(compiled.getEngine().getContext());
			try {
				LOG.trace("Calling script function: {} from script: {}.", callbackName);
				((Invocable) compiled.getEngine()).invokeFunction(callbackName);
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} catch (FileNotFoundException | ScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void startScriptInterval(Entity entity, int delay, String callbackFunctionName) {
		final ScriptComponent scriptComp = entityService.getComponent(entity, ScriptComponent.class)
				.orElseThrow(IllegalArgumentException::new);

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
