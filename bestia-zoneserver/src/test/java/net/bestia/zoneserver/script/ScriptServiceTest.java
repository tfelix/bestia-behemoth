package net.bestia.zoneserver.script;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Optional;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.ScriptComponent;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.actor.script.PeriodicScriptRunnerActor;

public class ScriptServiceTest {

	private static final long INVALID_SCRIPT_COMP_ID = 666;
	private static final long VALID_SCRIPT_COMP_ID = 123;
	//private static final String UNKNOWN_SCRIPT_FILE = "blubber.js";
	private static final String CALLBACK_FN_NAME = "callbackFunction";
	private static final String KNOWN_SCRIPT_FILE = "known.js";
	private static final long INVALID_ENTITY_ID = 888;
	private static final long VALID_ENTITY_ID = 890;

	private EntityService entityService;
	private ZoneAkkaApi akkaApi;
	private ScriptApi scriptApi;
	private ScriptCache cache;
	private ScriptService scriptService;
	private Entity scriptEntity;
	private Entity nonScriptEntity;
	private ScriptComponent scriptComponent;
	private ActorRef runnerRef;
	private ActorPath runnerRefPath;

	@Before
	public void setup() {
		
		scriptApi = mock(ScriptApi.class);
		entityService = mock(EntityService.class);
		akkaApi = mock(ZoneAkkaApi.class);
		cache = mock(ScriptCache.class);
		scriptEntity = mock(Entity.class);
		nonScriptEntity = mock(Entity.class);
		scriptComponent = mock(ScriptComponent.class);
		runnerRef = mock(ActorRef.class);
		runnerRefPath = mock(ActorPath.class);
		
		when(runnerRef.path()).thenReturn(runnerRefPath);
		
		// Actor ref can only be test probe
		// https://stackoverflow.com/questions/10868983/spock-mocks-for-akkas-actorref
		when(akkaApi.startUnnamedActor(PeriodicScriptRunnerActor.class)).thenReturn(runnerRef);

		when(entityService.getEntity(INVALID_ENTITY_ID)).thenReturn(null);
		when(entityService.getEntity(VALID_ENTITY_ID)).thenReturn(scriptEntity);
		when(entityService.getComponent(scriptEntity, ScriptComponent.class)).thenReturn(Optional.of(scriptComponent));
		when(entityService.hasComponent(scriptEntity, ScriptComponent.class)).thenReturn(true);
		when(entityService.getComponent(nonScriptEntity, ScriptComponent.class)).thenReturn(Optional.empty());
		when(entityService.hasComponent(nonScriptEntity, ScriptComponent.class)).thenReturn(false);
		when(entityService.getComponent(INVALID_SCRIPT_COMP_ID, ScriptComponent.class)).thenReturn(Optional.empty());
		when(entityService.getComponent(VALID_SCRIPT_COMP_ID, ScriptComponent.class))
				.thenReturn(Optional.of(scriptComponent));

		scriptService = new ScriptService(entityService, akkaApi, cache);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullArg1_throw() {
		new ScriptService(null, akkaApi, cache);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullArg2_throw() {
		new ScriptService(entityService, null, cache);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullArg3_throw() {
		new ScriptService(entityService, akkaApi, null);
	}

	@Test
	public void callScript_unkownScriptFileName_doesNothing() {
		//scriptService.deleteScriptComponent(INVALID_SCRIPT_COMP_ID);
	}

	@Test
	public void callScript_errorInScript_doesNothing() {
		scriptService.callScript(KNOWN_SCRIPT_FILE);
	}

	@Test(expected = IllegalArgumentException.class)
	public void triggerScriptIntervalCallback_invalidScriptId_throws() {
		scriptService.callScriptIntervalCallback(INVALID_SCRIPT_COMP_ID);
	}

	//@Test
	public void triggerScriptIntervalCallback_validScriptId_callsScriptFunction() {

	}

	@Test(expected = NullPointerException.class)
	public void startScriptInterval_nullEntity_throws() {
		scriptService.startScriptInterval(null, 123, CALLBACK_FN_NAME);
	}

	@Test(expected = IllegalArgumentException.class)
	public void startScriptInterval_negativeDelay_throws() {
		scriptService.startScriptInterval(scriptEntity, -123, CALLBACK_FN_NAME);
	}

	@Test(expected = IllegalArgumentException.class)
	public void startScriptInterval_nullDelay_throws() {
		scriptService.startScriptInterval(scriptEntity, 0, CALLBACK_FN_NAME);
	}

	@Test(expected = NullPointerException.class)
	public void startScriptInterval_nullCallbackName_throws() {
		scriptService.startScriptInterval(scriptEntity, 1230, null);
	}

	@Test
	public void startScriptInterval_validParams_startsActorAndSendMsg() {

	}

	@Test(expected = NullPointerException.class)
	public void stopScriptInterval_nullEntity_throws() {
		scriptService.stopScriptInterval(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void stopScriptInterval_nonScriptEntity_throws() {
		scriptService.stopScriptInterval(nonScriptEntity);
	}

	@Test
	public void stopScriptInterval_noIntervalSetBefore_doesNothing() {
		ArgumentCaptor<ScriptComponent> argument = ArgumentCaptor.forClass(ScriptComponent.class);

		scriptService.stopScriptInterval(scriptEntity);

		verify(entityService).getComponent(scriptEntity, ScriptComponent.class);
		verify(akkaApi, never()).sendToActor(any(ActorPath.class), any(PoisonPill.class));
		verify(entityService, never()).saveComponent(argument.capture());
	}

	@Test
	public void stopScriptInterval_intervalSetBefore_stopsInterval() {
		ArgumentCaptor<ScriptComponent> argument = ArgumentCaptor.forClass(ScriptComponent.class);

		scriptService.startScriptInterval(scriptEntity, 2000, CALLBACK_FN_NAME);
		scriptService.stopScriptInterval(scriptEntity);

		verify(entityService).getComponent(scriptEntity, ScriptComponent.class);
		verify(akkaApi).sendToActor(any(ActorPath.class), PoisonPill.getInstance());
		//verify(entityService).saveComponent(argument.capture());
		//Assert.assertTrue(argument.getValue().getClass().equals(ScriptComponent.class));
	}

	//@Test
	public void call_script_attaches_callbacks() throws ScriptException, FileNotFoundException, NoSuchMethodException {

		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("nashorn");

		engine.put("Bestia", scriptApi);

		ClassLoader classLoader = getClass().getClassLoader();
		File testFile = new File(classLoader.getResource("script/attack/create_aoe_dmg.js").getFile());
		engine.eval(new FileReader(testFile));

		((Invocable) engine).invokeFunction("main");

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
