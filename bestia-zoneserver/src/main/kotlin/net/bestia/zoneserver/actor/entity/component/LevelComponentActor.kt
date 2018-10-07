package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.entity.component.LevelComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
@HandlesComponent(LevelComponent::class)
class LevelComponentActor(
    levelComponent: LevelComponent
) : ComponentActor<LevelComponent>(levelComponent) {
  override fun createReceive(builder: ReceiveBuilder) {

  }
}