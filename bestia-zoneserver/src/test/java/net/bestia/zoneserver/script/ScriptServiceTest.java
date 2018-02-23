package net.bestia.zoneserver.script;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import net.entity.Entity;
import net.bestia.entity.EntityService;
import net.entity.component.ScriptComponent;
import net.bestia.messages.MessageApi;

@RunWith(MockitoJUnitRunner.class)
public class ScriptServiceTest {

	private static final long INVALID_SCRIPT_COMP_ID = 666;
	private static final long VALID_SCRIPT_COMP_ID = 123;
	// private static final String UNKNOWN_SCRIPT_FILE = "blubber.js";
	private static final String CALLBACK_FN_NAME = "callbackFunction";
	private static final String VALID_SCRIPT_FILE = "start.js";
	private static final String VALID_SCRIPT_UID = "12435-23345-233656-233446";
	private static final long INVALID_ENTITY_ID = 888;
	private static final long VALID_ENTITY_ID = 890;
	private static final String ERROR_SCRIPT_FILE = null;

	@Mock
	private EntityService entityService;
	@Mock
	private MessageApi akkaApi;
	@Mock
	private ScriptApi scriptApi;
	@Mock
	private ScriptCache cache;
	@Mock
	private ScriptService scriptService;
	@Mock
	private Entity scriptEntity;
	@Mock
	private Entity nonScriptEntity;
	
	@Mock
	private ScriptResolver resolver;
	
	@Mock
	private ScriptComponent scriptComponent;

	@Mock
	private ActorRef runnerRef;
	@Mock
	private ActorPath runnerRefPath;

	@Before
	public void setup() {

		when(runnerRef.path()).thenReturn(runnerRefPath);

		when(entityService.getEntity(INVALID_ENTITY_ID)).thenReturn(null);
		when(entityService.getEntity(VALID_ENTITY_ID)).thenReturn(scriptEntity);
		when(entityService.getComponent(scriptEntity, ScriptComponent.class)).thenReturn(Optional.of(scriptComponent));
		when(entityService.hasComponent(scriptEntity, ScriptComponent.class)).thenReturn(true);
		when(entityService.getComponent(nonScriptEntity, ScriptComponent.class)).thenReturn(Optional.empty());
		when(entityService.hasComponent(nonScriptEntity, ScriptComponent.class)).thenReturn(false);
		when(entityService.getComponent(INVALID_SCRIPT_COMP_ID, ScriptComponent.class)).thenReturn(Optional.empty());
		when(entityService.getComponent(VALID_SCRIPT_COMP_ID, ScriptComponent.class))
				.thenReturn(Optional.of(scriptComponent));

		scriptService = new ScriptService(entityService, akkaApi, cache, resolver);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullArg1_throw() {
		new ScriptService(null, akkaApi, cache, resolver);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullArg2_throw() {
		new ScriptService(entityService, null, cache, resolver);
	}

	@Test(expected = NullPointerException.class)
	public void ctor_nullArg3_throw() {
		new ScriptService(entityService, akkaApi, null, resolver);
	}
	
	@Test(expected = NullPointerException.class)
	public void ctor_nullArg4_throw() {
		new ScriptService(entityService, akkaApi, cache, null);
	}

	@Test
	public void callScript_unkownScriptFileName_doesNothing() {
		// scriptService.deleteScriptComponent(INVALID_SCRIPT_COMP_ID);
	}

	@Test
	public void callScript_errorInScript_doesNothing() {
		scriptService.callScriptMainFunction(ERROR_SCRIPT_FILE);
	}

	@Test
	public void callScript_validScript_ok() {
		scriptService.callScriptMainFunction(VALID_SCRIPT_FILE);
	}

	@Test(expected = IllegalArgumentException.class)
	public void triggerScriptIntervalCallback_invalidScriptId_throws() {
		scriptService.callScriptIntervalCallback(INVALID_SCRIPT_COMP_ID, VALID_SCRIPT_UID);
	}

	// @Test
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
		
		verify(akkaApi, never()).sendToEntity(any());
		verify(entityService, never()).updateComponent(argument.capture());
	}

	@Test
	public void stopScriptInterval_intervalSetBefore_stopsInterval() {

		scriptService.startScriptInterval(scriptEntity, 2000, CALLBACK_FN_NAME);
		scriptService.stopScriptInterval(scriptEntity);

		verify(entityService).getComponent(scriptEntity, ScriptComponent.class);
		verify(akkaApi).sendToEntity(any(EntityKillMessage.class));
	}

}
