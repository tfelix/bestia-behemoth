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

import akka.actor.ActorRef;
import net.bestia.messages.internal.ScriptIntervalMessage;
import net.bestia.model.geometry.CollisionShape;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.actor.script.PeriodicScriptRunnerActor;
import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.ScriptComponent;

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

	public class TestApi implements ScriptApi {

		public void setCallback(Runnable obj) {
			System.out.println(obj.toString());
			ScriptEngine eng = (ScriptEngine) obj;
			// eng.g

			obj.run();
			obj.run();
			obj.run();
		}

		public void call1() {
			System.out.println("call 1");
		}

		@Override
		public void info(String text) {
			System.out.println(text);
		}

		@Override
		public void debug(String text) {
			System.out.println(text);
		}

		@Override
		public ScriptEntityWrapper createSpellEntity(CollisionShape shape, String spriteName, int baseDuration) {
			// TODO Auto-generated method stub
			return null;
		}
	}

	@Autowired
	public ScriptService(EntityService entityService, ZoneAkkaApi akkaApi) {

		this.entityService = Objects.requireNonNull(entityService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
		// this.scriptApi = Objects.requireNonNull(scriptApi);
		this.scriptApi = new TestApi();
	}

	public void saveData(String scriptUid, String json) {

		LOG.debug("Saving script data for script: {} data: {}.", scriptUid, json);
	}

	public void loadData(String scriptUid) {

		LOG.debug("Loading script data for script: {} data: {}.", scriptUid, "nothing");
	}

	public void triggerScriptInterval(long scriptEntityId) {

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
		// Tell the actor which script to periodically call.
		final ScriptIntervalMessage message = new ScriptIntervalMessage(entity.getId(), delay);
		scriptRunner.tell(message, ActorRef.noSender());

		scriptComp.setScriptActorPath(scriptRunner.path());
		scriptComp.setOnIntervalCallbackName(callbackFunctionName);

		// FIXME Das hier ist temporär zum testen. Muss später besser gelöst
		// werden.
		scriptComp.setScriptType(ScriptType.ATTACK);
		scriptComp.setScriptName("create_aoe_dmg");

		entityService.saveComponent(scriptComp);
	}
}
