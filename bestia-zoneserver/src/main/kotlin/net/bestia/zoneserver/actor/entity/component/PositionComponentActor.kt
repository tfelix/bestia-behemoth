package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.chat.PositionToMessage
import net.bestia.zoneserver.entity.component.PositionComponent

@ActorComponent(PositionComponent::class)
class PositionComponentActor(
    positionComponent: PositionComponent
) : ComponentActor<PositionComponent>(positionComponent) {

  override fun createReceive(builder: ReceiveBuilder) {
    builder.match(PositionToMessage::class.java, this::positionTo)
  }

  private fun positionTo(msg: PositionToMessage) {
    component = component.copy(shape = component.shape.moveTo(msg.position))
  }
}