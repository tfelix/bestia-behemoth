package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.component.PositionComponent

@ActorComponent(PositionComponent::class, broadcastToClients = true)
class PositionComponentActor(
    positionComponent: PositionComponent,
    private val entityCollisionService: EntityCollisionService
) : ComponentActor<PositionComponent>(positionComponent) {

  override fun onComponentChanged(oldComponent: PositionComponent, newComponent: PositionComponent) {
    entityCollisionService.updateEntityCollision(newComponent.entityId, newComponent.shape)
  }
}