package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.entity.transmit.OwnerTransmitFilter
import net.bestia.zoneserver.entity.component.TemperatureComponent

@ActorComponent(
    component = TemperatureComponent::class,
    transmitFilter = OwnerTransmitFilter::class
)
class TemperatureComponentActor(
    tempComponent: TemperatureComponent
) : ComponentActor<TemperatureComponent>(tempComponent)