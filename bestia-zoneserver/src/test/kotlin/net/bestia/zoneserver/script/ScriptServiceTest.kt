package net.bestia.zoneserver.script

import akka.actor.ActorPath
import akka.actor.ActorRef
import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.ScriptComponent
import net.bestia.messages.MessageApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.util.*

@RunWith(MockitoJUnitRunner::class)
class ScriptServiceTest {

  @Mock
  private val entityService: EntityService? = null
  @Mock
  private val akkaApi: MessageApi? = null
  @Mock
  private val cache: ScriptCache? = null
  @Mock
  private var scriptService: ScriptService? = null
  @Mock
  private val scriptEntity: Entity? = null
  @Mock
  private val nonScriptEntity: Entity? = null

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

  @Test
  fun startScriptInterval_validParams_startsActorAndSendMsg() {

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
