package net.bestia.zoneserver.actor.entity

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.entity.EntityInteractionMessage
import net.bestia.messages.entity.EntityInteractionRequest
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.entity.InteractionService
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.beans.factory.annotation.Qualifier

private val LOG = KotlinLogging.logger { }

/**
 * Receives interaction requests for an entity. It will query the system and ask
 * the entity how the player is able to interact with it.
 *
 * @author Thomas Felix
 */
@Actor
class EntityInteractionRequestActor(
    private val interactService: InteractionService,
    private val playerEntityService: PlayerEntityService,
    private val messageApi: MessageApi,
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val sendClientActor: ActorRef
) : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.matchRedirect(EntityInteractionRequest::class.java, this::onInteractionRequest)
  }

  private fun onInteractionRequest(msg: EntityInteractionRequest) {
    LOG.debug{ "Received message: $msg" }

    val activeEntityId = playerEntityService.getActivePlayerEntityId(msg.accountId) ?: return

    awaitEntityResponse(messageApi, context, setOf(activeEntityId, msg.interactedEntityId)) {
      val interactions = interactService.getPossibleInteractions(
          it[activeEntityId],
          it[msg.interactedEntityId]
      )

      val reply = EntityInteractionMessage(
          msg.accountId,
          msg.entityId,
          interactions
      )

      sendClientActor.tell(reply, self)
    }
  }

  companion object {
    const val NAME = "requestInteract"
  }
}
