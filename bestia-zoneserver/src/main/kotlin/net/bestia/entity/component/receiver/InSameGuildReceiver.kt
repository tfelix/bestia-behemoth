package net.bestia.entity.component.receiver

import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.Component
import net.bestia.entity.component.PlayerComponent
import net.bestia.entity.component.PositionComponent
import net.bestia.getOrNull
import net.bestia.zoneserver.entity.EntitySearchService
import net.bestia.zoneserver.map.MapService

@org.springframework.stereotype.Component
class InSameGuildReceiver(
        private val entitySearchService: EntitySearchService
) : SyncReceiver {

  override fun gatherReceiver(
          entity: Entity,
          component: Component,
          entityService: EntityService
  ): Collection<Receiver> {
    val position = entityService.getComponent(entity, PositionComponent::class.java).getOrNull()
            ?: return emptySet()
    val viewRect = MapService.getViewRect(position.position)
    val allEntities = entitySearchService.getCollidingEntities(viewRect)
    return allEntities.filter { entityService.hasComponent(it, PlayerComponent::class.java) }
            .map {
              val clientId = entityService.getComponent(it, PlayerComponent::class.java).get()
              ClientReceiver(clientId.ownerAccountId)
            }
  }
}