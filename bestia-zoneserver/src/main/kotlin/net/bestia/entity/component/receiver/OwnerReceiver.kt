package net.bestia.entity.component.receiver

import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.Component
import net.bestia.entity.component.PlayerComponent
import net.bestia.getOrNull

/**
 * This returns the owner of this bestia.
 */
class OwnerReceiver : SyncReceiver {
  override fun gatherReceiver(entity: Entity, component: Component, entityService: EntityService): List<Receiver> {
    return entityService.getComponent(entity, PlayerComponent::class.java).getOrNull()?.let {
      listOf(ClientReceiver(it.ownerAccountId))
    } ?: listOf()
  }
}