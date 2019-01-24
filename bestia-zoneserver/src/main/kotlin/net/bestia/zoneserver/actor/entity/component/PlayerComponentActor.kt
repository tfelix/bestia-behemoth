package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.PlayerComponent

@ActorComponent(PlayerComponent::class)
class PlayerComponentActor(
    playerComponent: PlayerComponent
) : ComponentActor<PlayerComponent>(playerComponent) {
}