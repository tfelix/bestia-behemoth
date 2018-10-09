package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.entity.MetaDataComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
@HandlesComponent(MetaDataComponent::class)
class MetaDataComponentActor(
    component: MetaDataComponent
) : ComponentActor<MetaDataComponent>(component) {
  override fun createReceive(builder: ReceiveBuilder) {
  }
}