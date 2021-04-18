package net.bestia.zoneserver.actor.entity.transmit

import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityId
import net.bestia.zoneserver.entity.component.OwnerComponent
import org.springframework.stereotype.Component

/**
 * Sends the component only to the owner of the entity.
 */
@Component
class OwnerTransmitFilter : TransmitFilter {
  override fun findTransmitCandidates(transmit: TransmitRequest): Set<EntityId> {
    return emptySet()
  }

  override fun selectTransmitTargetAccountIds(candidates: Set<Entity>, transmit: TransmitRequest): Set<Long> {
    val ownerComp = transmit.entity.tryGetComponent(OwnerComponent::class.java)
        ?: return emptySet()

    return ownerComp.ownerAccountIds
  }
}