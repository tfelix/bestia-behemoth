package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.entity.component.PlayerComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
@HandlesComponent(PlayerComponent::class)
class PlayerComponentActor(
    playerComponent: PlayerComponent
) : ComponentActor<PlayerComponent>(playerComponent) {
  override fun createReceive(builder: ReceiveBuilder) {

  }
}