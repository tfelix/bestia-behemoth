package net.bestia.zoneserver.script

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

import java.util.Optional

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

import akka.actor.ActorPath
import akka.actor.ActorRef
import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.ScriptComponent
import net.bestia.messages.MessageApi

@RunWith(MockitoJUnitRunner::class)
class ScriptServiceTest {

  @Mock
  private val entityService: EntityService? = null
  @Mock
  private val akkaApi: MessageApi? = null
  @Mock
  private val scriptApi: ScriptApi? = null
  @Mock
  private val cache: ScriptCache? = null
  @Mock
  private var scriptService: ScriptService? = null
  @Mock
  private val scriptEntity: Entity? = null
  @Mock
  private val nonScriptEntity: Entity? = null

  @Mock
  private val resolver: ScriptResolver? = null

  @Mock
  private val scriptComponent: ScriptComponent? = null

  @Mock
  private val runnerRef: ActorRef? = null
  @Mock
  private val runnerRefPath: ActorPath? = null

  @Before
  fun setup() {

    `when`(runnerRef!!.path()).thenReturn(runnerRefPath)

    `when`(entityService!!.getEntity(INVALID_ENTITY_ID)).thenReturn(null)
    `when`(entityService.getEntity(VALID_ENTITY_ID)).thenReturn(scriptEntity)
    `when`(entityService.getComponent(scriptEntity, ScriptComponent::class.java)).thenReturn(Optional.of(scriptComponent!!))
    `when`(entityService.hasComponent(scriptEntity, ScriptComponent::class.java)).thenReturn(true)
    `when`(entityService.getComponent(nonScriptEntity, ScriptComponent::class.java)).thenReturn(Optional.empty())
    `when`(entityService.hasComponent(nonScriptEntity, ScriptComponent::class.java)).thenReturn(false)
    `when`(entityService.getComponent(INVALID_SCRIPT_COMP_ID, ScriptComponent::class.java)).thenReturn(Optional.empty())
    `when`(entityService.getComponent(VALID_SCRIPT_COMP_ID, ScriptComponent::class.java))
            .thenReturn(Optional.of(scriptComponent))

    scriptService = ScriptService(entityService, akkaApi, cache, resolver)
  }

  @Test
  fun callScript_unkownScriptFileName_doesNothing() {
    // scriptService.deleteScriptComponent(INVALID_SCRIPT_COMP_ID);
  }

  @Test
  fun callScript_errorInScript_doesNothing() {
    scriptService!!.callScriptMainFunction(ERROR_SCRIPT_FILE)
  }

  @Test
  fun callScript_validScript_ok() {
    scriptService!!.callScriptMainFunction(VALID_SCRIPT_FILE)
  }

  @Test(expected = IllegalArgumentException::class)
  fun triggerScriptIntervalCallback_invalidScriptId_throws() {
    scriptService!!.callScriptIntervalCallback(INVALID_SCRIPT_COMP_ID, VALID_SCRIPT_UID)
  }

  // @Test
  fun triggerScriptIntervalCallback_validScriptId_callsScriptFunction() {

  }

  @Test(expected = NullPointerException::class)
  fun startScriptInterval_nullEntity_throws() {
    scriptService!!.startScriptInterval(null, 123, CALLBACK_FN_NAME)
  }

  @Test(expected = IllegalArgumentException::class)
  fun startScriptInterval_negativeDelay_throws() {
    scriptService!!.startScriptInterval(scriptEntity, -123, CALLBACK_FN_NAME)
  }

  @Test(expected = IllegalArgumentException::class)
  fun startScriptInterval_nullDelay_throws() {
    scriptService!!.startScriptInterval(scriptEntity, 0, CALLBACK_FN_NAME)
  }

  @Test(expected = NullPointerException::class)
  fun startScriptInterval_nullCallbackName_throws() {
    scriptService!!.startScriptInterval(scriptEntity, 1230, null)
  }

  @Test
  fun startScriptInterval_validParams_startsActorAndSendMsg() {

  }

  @Test(expected = NullPointerException::class)
  fun stopScriptInterval_nullEntity_throws() {
    scriptService!!.stopScriptInterval(null)
  }

  @Test(expected = IllegalArgumentException::class)
  fun stopScriptInterval_nonScriptEntity_throws() {
    scriptService!!.stopScriptInterval(nonScriptEntity)
  }

  @Test
  fun stopScriptInterval_noIntervalSetBefore_doesNothing() {
    val argument = ArgumentCaptor.forClass(ScriptComponent::class.java)

    scriptService!!.stopScriptInterval(scriptEntity)

    verify<EntityService>(entityService).getComponent(scriptEntity, ScriptComponent::class.java)

    verify<MessageApi>(akkaApi, never()).sendToEntity(any())
    verify<EntityService>(entityService, never()).updateComponent(argument.capture())
  }

  @Test
  fun stopScriptInterval_intervalSetBefore_stopsInterval() {

    scriptService!!.startScriptInterval(scriptEntity, 2000, CALLBACK_FN_NAME)
    scriptService!!.stopScriptInterval(scriptEntity)

    verify<EntityService>(entityService).getComponent(scriptEntity, ScriptComponent::class.java)
    verify<MessageApi>(akkaApi).sendToEntity(any(EntityKillMessage::class.java!!))
  }

  companion object {

    private val INVALID_SCRIPT_COMP_ID: Long = 666
    private val VALID_SCRIPT_COMP_ID: Long = 123
    // private static final String UNKNOWN_SCRIPT_FILE = "blubber.js";
    private val CALLBACK_FN_NAME = "callbackFunction"
    private val VALID_SCRIPT_FILE = "start.js"
    private val VALID_SCRIPT_UID = "12435-23345-233656-233446"
    private val INVALID_ENTITY_ID: Long = 888
    private val VALID_ENTITY_ID: Long = 890
    private val ERROR_SCRIPT_FILE: String? = null
  }

}
