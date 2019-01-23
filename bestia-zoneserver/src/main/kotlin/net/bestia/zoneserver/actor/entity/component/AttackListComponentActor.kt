package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.entity.component.AttackListComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
@HandlesComponent(AttackListComponent::class)
class AttackListComponentActor(
    attackListComponent: AttackListComponent
) : ComponentActor<AttackListComponent>(attackListComponent) {
}