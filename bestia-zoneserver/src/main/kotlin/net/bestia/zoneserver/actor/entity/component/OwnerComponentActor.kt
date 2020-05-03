package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.OwnerComponent

@ActorComponent(OwnerComponent::class)
class OwnerComponentActor(
    ownerComponent: OwnerComponent
) : ComponentActor<OwnerComponent>(ownerComponent)