package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.entity.transmit.InRangeTransmitFilter
import net.bestia.zoneserver.entity.component.VisualComponent

@ActorComponent(VisualComponent::class, transmitFilter = InRangeTransmitFilter::class)
class VisualComponentActor(
    visualComponent: VisualComponent
) : ComponentActor<VisualComponent>(visualComponent)