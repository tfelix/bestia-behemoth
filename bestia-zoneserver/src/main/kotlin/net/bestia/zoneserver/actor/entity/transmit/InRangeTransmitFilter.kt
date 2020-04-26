package net.bestia.zoneserver.actor.entity.transmit

import akka.actor.ActorRef
import akka.actor.ActorRefFactory
import mu.KotlinLogging
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.MessageApi
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
  override fun findTransmitTargets(transmit: TransmitRequest, ctx: ActorRefFactory, parent: ActorRef) {
    val posComp = transmit.entity.tryGetComponent(PositionComponent::class.java)
        ?: run {
          LOG.warn { "Position component of entity '${transmit.entity}' not present" }
          return
        }

    val updateRect = MapService.getUpdateRect(posComp.position)
    val activeIds = entityCollisionService.getAllCollidingEntityIds(updateRect)

    awaitEntityResponse(messageApi, ctx, activeIds) { entities ->
      val receivingClientIds = entities.all
          .mapNotNull { it.tryGetComponent(OwnerComponent::class.java)?.ownerAccountIds }
          .flatten()
          .toSet()

      parent.tell(TransmitCommand(
          entity = transmit.entity,
          changedComponent = transmit.changedComponent,
          receivingClientIds = receivingClientIds
      ), ActorRef.noSender())
    }
  }
}