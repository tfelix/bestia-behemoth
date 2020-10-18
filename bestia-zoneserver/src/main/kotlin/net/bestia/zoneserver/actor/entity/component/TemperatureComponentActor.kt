package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.TemperatureComponent

@ActorComponent(TemperatureComponent::class)
class TemperatureComponentActor(
    tempComponent: TemperatureComponent
) : ComponentActor<TemperatureComponent>(tempComponent)