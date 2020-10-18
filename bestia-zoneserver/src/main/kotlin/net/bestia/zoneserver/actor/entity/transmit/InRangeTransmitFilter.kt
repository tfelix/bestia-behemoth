package net.bestia.zoneserver.actor.entity.transmit

import mu.KotlinLogging
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.component.OwnerComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.map.MapService
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

@Component
class InRangeTransmitFilter(
    private val entityCollisionService: EntityCollisionService,
    private val messageApi: MessageApi
) : TransmitFilter {

  override fun findTransmitCandidates(transmit: TransmitRequest): Set<Long> {
    val posComp = transmit.entity.tryGetComponent(PositionComponent::class.java)
        ?: run {
          LOG.warn { "Position component of entity '${transmit.entity}' not present" }
          return emptySet()
        }

    val updateRect = MapService.getUpdateRect(posComp.position)

    return entityCollisionService.getAllCollidingEntityIds(updateRect)
  }

  override fun selectTransmitTargets(candidates: Set<Entity>, transmit: TransmitRequest): Set<Long> {
    return candidates
        .mapNotNull { it.tryGetComponent(OwnerComponent::class.java)?.ownerAccountIds }
        .flatten()
        .toSet()
  }
}