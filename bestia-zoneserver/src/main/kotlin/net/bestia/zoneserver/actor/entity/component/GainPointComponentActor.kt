package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.GainPointComponent

@ActorComponent(GainPointComponent::class)
class GainPointComponentActor(
    gainPointComponent: GainPointComponent
) : ComponentActor<GainPointComponent>(gainPointComponent)