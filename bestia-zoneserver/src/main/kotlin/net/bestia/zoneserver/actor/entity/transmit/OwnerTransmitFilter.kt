package net.bestia.zoneserver.actor.entity.transmit

import akka.actor.ActorRef
import akka.actor.ActorRefFactory
import net.bestia.zoneserver.entity.component.OwnerComponent
import org.springframework.stereotype.Component

/**
 * Sends the component only to the owner of the entity.
 */
@Component
class OwnerTransmitFilter : TransmitFilter {
  override fun findTransmitTargets(transmit: TransmitRequest, ctx: ActorRefFactory, parent: ActorRef) {
    val ownerComp = transmit.entity.tryGetComponent(OwnerComponent::class.java)
        ?: return

    parent.tell(TransmitCommand(
        entity = transmit.entity,
        changedComponent = transmit.changedComponent,
        receivingClientIds = ownerComp.ownerAccountIds
    ), ActorRef.noSender())
  }
}