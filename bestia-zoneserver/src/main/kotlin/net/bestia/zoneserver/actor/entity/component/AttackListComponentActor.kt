package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.entity.transmit.OwnerTransmitFilter
import net.bestia.zoneserver.entity.component.AttackListComponent

@ActorComponent(AttackListComponent::class)
class AttackListComponentActor(
    attackListComponent: AttackListComponent
) : ComponentActor<AttackListComponent>(attackListComponent)