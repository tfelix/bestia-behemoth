package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.EquipComponent

@ActorComponent(EquipComponent::class)
class EquipComponentActor(
    equipComponent: EquipComponent
) : ComponentActor<EquipComponent>(equipComponent)