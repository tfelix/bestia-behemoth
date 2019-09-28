package net.bestia.zoneserver.actor.client

import mu.KotlinLogging
import net.bestia.messages.AccountMessage
import net.bestia.messages.client.ClientEnvelope
import net.bestia.model.geometry.Rect
import net.bestia.zoneserver.actor.MessageApi
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.PlayerComponent

private val LOG = KotlinLogging.logger { }

internal data class SendInRange(
    val entity: Entity,
    val content: AccountMessage
)

/***
 * If a [EntityJsonMessage] is received by this actor it will check if the
 * given entity contains a [PositionComponent] and if it does it will
 * detect all active player entities in the update range of the game and forward
 * the message to them via a [SendToClientActor].
 *
 * @author Thomas Felix
 */
@Actor
class SendClientsInRangeActor(
    private val entityCollisionService: EntityCollisionService,
    private val messageApi: MessageApi
) : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.match(SendInRange::class.java, this::handleSendToActiveInRange)
  }

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  /**
   * Sends to all active players in range. Maybe automatically detecting this
   * method by message type does not work out. Then we might need to create a
   * whole new actor only responsible for sending active range messages. Might
   * be better idea anyways.
   */
  private fun handleSendToActiveInRange(msg: SendInRange) {
    // Get position of the entity.
    val posComp = msg.entity.tryGetComponent(PositionComponent::class.java)
        ?: run {
          LOG.warn { "Position component of entity in message $msg not present. Can not send range update." }
          return
        }

    // FIXME Get the proper rect
    val updateRect = Rect(0, 0, 0, 10, 10, 10)// MapService.getUpdateRect(posComp.position)
    val activeIds = entityCollisionService.getAllCollidingEntityIds(updateRect)

    awaitEntityResponse(messageApi, context, activeIds) { entities ->
      val playerAccountIds = entities.all
          .mapNotNull { it.tryGetComponent(PlayerComponent::class.java)?.ownerAccountId }
      playerAccountIds.forEach {
        sendClient.tell(ClientEnvelope(it, msg.content), self)
      }
    }
  }

  companion object {
    const val NAME = "sendToClientsInRange"
  }
}

