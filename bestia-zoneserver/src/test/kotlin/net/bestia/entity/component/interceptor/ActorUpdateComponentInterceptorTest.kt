package net.bestia.entity.component.interceptor

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.Component
import net.bestia.messages.ComponentMessageEnvelope
import net.bestia.zoneserver.actor.AkkaMessageApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ActorUpdateComponentInterceptorTest {

  @ComponentActor(value = "net.bestia.zoneserver.actor.entity.component.ScriptComponentActor", updateActorOnChange = true)
  class AnnotatedComponent(
          id: Long
  ) : Component(id)

  class NotAnnotatedComponent(
          id: Long
  ) : Component(id)

  @Mock
  private lateinit var msgApi: AkkaMessageApi

  @Mock
  private lateinit var entityService: EntityService

  private val entity = Entity(1)
  private val annotatedComp = AnnotatedComponent(10)
  private val notAnnotatedComp = NotAnnotatedComponent(11)

  private lateinit var interceptor: ActorUpdateComponentInterceptor

  @Before
  fun setup() {
    interceptor = ActorUpdateComponentInterceptor(msgApi)
  }

  @Test
  fun onDeleteAction_propagates_annotated_component() {
    interceptor.triggerDeleteAction(entityService, entity, annotatedComp)
    verify(msgApi).sendToEntity(eq(entity.id), eq(ComponentMessageEnvelope::class.java))
  }

  @Test
  fun onDeleteAction_doesnt_propagates_not_annotated_component() {
    interceptor.triggerDeleteAction(entityService, entity, notAnnotatedComp)
    verify(msgApi, times(0)).sendToEntity(any(), any())
  }

  @Test
  fun onUpdateAction_propagates_annotated_component() {
    interceptor.triggerUpdateAction(entityService, entity, annotatedComp)
    verify(msgApi).sendToEntity(eq(entity.id), eq(ComponentMessageEnvelope::class.java))
  }

  @Test
  fun onUpdateAction_doesnt_propagates_not_annotated_component() {
    interceptor.triggerUpdateAction(entityService, entity, notAnnotatedComp)
    verify(msgApi, times(0)).sendToEntity(any(), any())
  }

  @Test
  fun onCreateAction_propagates_annotated_component() {
    interceptor.triggerUpdateAction(entityService, entity, annotatedComp)
    verify(msgApi).sendToEntity(eq(entity.id), eq(ComponentMessageEnvelope::class.java))
  }

  @Test
  fun onCreateAction_doesnt_propagates_not_annotated_component() {
    interceptor.triggerUpdateAction(entityService, entity, notAnnotatedComp)
    verify(msgApi, times(0)).sendToEntity(any(), any())
  }
}