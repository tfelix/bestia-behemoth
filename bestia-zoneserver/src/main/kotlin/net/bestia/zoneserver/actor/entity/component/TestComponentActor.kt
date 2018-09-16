package net.bestia.zoneserver.actor.entity.component

import akka.actor.ActorRef
import akka.japi.pf.ReceiveBuilder
import net.bestia.entity.component.TestComponent
import net.bestia.messages.Envelope
import net.bestia.zoneserver.UpdateTestComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

sealed class TestComponentMessage

data class InstallComponentMessage<out T : net.bestia.entity.component.Component>(
        val component: T
) : TestComponentMessage()

data class RequestComponentMessage(
        val requester: ActorRef
)

data class ResponseComponentMessage<out T : net.bestia.entity.component.Component>(
        val component: T
)

data class ComponentBroadcastEnvelope(
        override val content: Any
) : Envelope

class TestComponentModify(
        val newContent: String
)

@Component
@Scope("prototype")
class TestComponentActor(
        comp: TestComponent
) : BaseComponentActor<TestComponent>(comp) {

  override fun createReceive(builder: ReceiveBuilder) {
    builder.match(TestComponentModify::class.java) {
      component = TestComponent(component.id, it.newContent).also { it.entityId = component.entityId }
    }.match(UpdateTestComponent::class.java, this::updateComponent)
  }

  private fun updateComponent(msg: UpdateTestComponent) {
    component = TestComponent(componentId = component.id, content = component.content + msg.addedText)
  }
}