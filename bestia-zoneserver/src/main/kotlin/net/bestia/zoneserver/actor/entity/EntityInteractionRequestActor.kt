package net.bestia.zoneserver.actor.entity

import mu.KotlinLogging
import net.bestia.entity.EntityService
import net.bestia.messages.entity.EntityInteractionMessage
import net.bestia.messages.entity.EntityInteractionRequestMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.entity.InteractionService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Receives interaction requests for an entity. It will query the system and ask
 * the entity how the player is able to interact with it.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class EntityInteractionRequestActor(
        private val entityService: EntityService,
        private val interactService: InteractionService
) : BaseClientMessageRouteActor() {

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.match(EntityInteractionRequestMessage::class.java, this::onInteractionRequest)
  }

  private fun onInteractionRequest(msg: EntityInteractionRequestMessage) {
    LOG.debug("Received message: {}", msg)

    // TODO Ist das hier manipulationssicher oder sendet uns der user beliebige entity ids?
    val interactions = interactService.getPossibleInteractions(msg.entityId,
            msg.interactedEntityId)

    val reply = EntityInteractionMessage(
            msg.accountId,
            msg.entityId,
            interactions)

    sendClient.tell(reply, self)
  }

  companion object {
    const val NAME = "requestInteract"
  }

}
