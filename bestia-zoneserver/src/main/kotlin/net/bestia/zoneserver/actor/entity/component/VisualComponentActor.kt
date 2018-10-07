package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.entity.component.VisualComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
@HandlesComponent(VisualComponent::class)
class VisualComponentActor(
    visualComponent: VisualComponent
) : ComponentActor<VisualComponent>(visualComponent) {
  override fun createReceive(builder: ReceiveBuilder) {

  }
}