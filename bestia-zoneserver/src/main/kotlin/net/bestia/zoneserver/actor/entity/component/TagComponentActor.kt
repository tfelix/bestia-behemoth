package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.TagComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@ActorComponent
@HandlesComponent(TagComponent::class)
class TagComponentActor(
    tagComponent: TagComponent
) : ComponentActor<TagComponent>(tagComponent) {
  override fun createReceive(builder: ReceiveBuilder) {

  }
}