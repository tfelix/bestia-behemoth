package net.bestia.zoneserver.actor.client

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.AccountMessage
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.component.OwnerComponent
import net.bestia.zoneserver.map.MapService
import org.springframework.beans.factory.annotation.Qualifier

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
    private val messageApi: MessageApi,
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val sendClientActor: ActorRef
) : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.matchRedirect(SendInRange::class.java, this::handleSendToActiveInRange)
  }

  /**
   * Sends to all active players in range. Maybe automatically detecting this
   * method by message type does not work out. Then we might need to create a
   * whole new actor only responsible for sending active range messages. Might
   * be better idea anyways.
   */
  private fun handleSendToActiveInRange(msg: SendInRange) {
    LOG.trace { "Received: $msg" }

    val posComp = msg.entity.tryGetComponent(PositionComponent::class.java)
        ?: run {
          LOG.warn { "Position component of entity in message $msg not present. Can not send range update." }
          return
        }

    val updateRect = MapService.getUpdateRect(posComp.position)
    val activeIds = entityCollisionService.getAllCollidingEntityIds(updateRect)

    awaitEntityResponse(messageApi, context, activeIds) { entities ->
      val playerAccountIds = entities.all
          .mapNotNull { it.tryGetComponent(OwnerComponent::class.java)?.ownerAccountIds }
          .flatten()
          .toSet()
      playerAccountIds.forEach {
        sendClientActor.tell(ClientEnvelope(it, msg.content), self)
      }
    }
  }

  companion object {
    const val NAME = "sendToClientsInRange"
  }
}

