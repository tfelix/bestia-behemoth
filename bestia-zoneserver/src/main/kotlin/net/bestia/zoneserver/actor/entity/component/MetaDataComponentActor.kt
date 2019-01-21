package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.MetaDataComponent

@ActorComponent
@HandlesComponent(MetaDataComponent::class)
class MetaDataComponentActor(
    component: MetaDataComponent
) : ComponentActor<MetaDataComponent>(component) {
  override fun createReceive(builder: ReceiveBuilder) {
  }
}