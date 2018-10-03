package net.bestia.zoneserver.actor.entity

import mu.KotlinLogging
import net.bestia.messages.entity.EntityInteractionMessage
import net.bestia.messages.entity.EntityInteractionRequestMessage
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.awaitEntityResponse
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.entity.InteractionService
import net.bestia.zoneserver.entity.PlayerEntityService
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
    private val interactService: InteractionService,
    private val playerEntityService: PlayerEntityService,
    private val messageApi: MessageApi
) : BaseClientMessageRouteActor() {

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.match(EntityInteractionRequestMessage::class.java, this::onInteractionRequest)
  }

  private fun onInteractionRequest(msg: EntityInteractionRequestMessage) {
    LOG.debug("Received message: {}", msg)

    val activeEntityId = playerEntityService.getActivePlayerEntityId(msg.accountId) ?: return

    awaitEntityResponse(messageApi, context, setOf(activeEntityId, msg.interactedEntityId)) {
      val interactions = interactService.getPossibleInteractions(
          it[activeEntityId],
          it[msg.interactedEntityId]
      )

      val reply = EntityInteractionMessage(
          msg.accountId,
          msg.entityId,
          interactions)

      sendClient.tell(reply, self)
    }
  }

  companion object {
    const val NAME = "requestInteract"
  }
}
