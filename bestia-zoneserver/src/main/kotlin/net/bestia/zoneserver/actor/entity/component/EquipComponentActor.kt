package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.EquipComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@ActorComponent
@HandlesComponent(EquipComponent::class)
class EquipComponentActor(
    equipComponent: EquipComponent
) : ComponentActor<EquipComponent>(equipComponent) {
}