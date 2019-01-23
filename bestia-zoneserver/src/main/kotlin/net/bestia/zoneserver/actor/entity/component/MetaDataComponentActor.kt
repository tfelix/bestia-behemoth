package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.MetaDataComponent

@ActorComponent(MetaDataComponent::class)
class MetaDataComponentActor(
    component: MetaDataComponent
) : ComponentActor<MetaDataComponent>(component)