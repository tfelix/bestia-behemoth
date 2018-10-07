package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.model.geometry.Point
import net.bestia.zoneserver.entity.component.PositionComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

internal data class SetPositionMessage(
    val position: Point
)

@Component
@Scope("prototype")
@HandlesComponent(PositionComponent::class)
class PositionComponentActor(
    positionComponent: PositionComponent
) : ComponentActor<PositionComponent>(positionComponent) {

  override fun createReceive(builder: ReceiveBuilder) {
    builder.match(PositionComponent::class.java, this::handleComponent)
    builder.match(SetPositionMessage::class.java, this::handlePositionSet)
  }

  private fun handlePositionSet(msg: SetPositionMessage) {
    component.position = msg.position
  }

  private fun handleComponent(comp: PositionComponent) {
    component = comp
  }
}