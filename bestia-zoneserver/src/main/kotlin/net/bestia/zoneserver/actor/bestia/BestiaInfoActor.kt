package net.bestia.zoneserver.actor.bestia

import mu.KotlinLogging
import net.bestia.messages.bestia.BestiaInfoRequestMessage
import net.bestia.messages.client.ToClientEnvelope
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.entity.SendToEntityActor
import net.bestia.zoneserver.actor.entity.component.ComponentBroadcastEnvelope
import net.bestia.zoneserver.actor.entity.component.RequestComponentMessage
import net.bestia.zoneserver.actor.entity.component.ResponseComponentMessage
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * This actor gathers all needed information about the bestias in the players
 * possession and will deliver this information to the player.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class BestiaInfoActor(
        private val playerEntityService: PlayerEntityService
) : BaseClientMessageRouteActor() {

  private val sendEntity = SpringExtension.actorOf(context, SendToEntityActor::class.java)
  private val sendToClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.match(BestiaInfoRequestMessage::class.java, this::handleInfoRequest)
  }

  private fun handleInfoRequest(msg: BestiaInfoRequestMessage) {
    LOG.debug(String.format("Received: %s", msg.toString()))

    val accId = msg.accountId
    val bestiasEids = playerEntityService.getPlayerEntities(accId).map { it.id }.toSet()
    waitForResponses(accId, bestiasEids)
  }

  private fun waitForResponses(accountId: Long, bestiasEids: Set<Long>) {
    val wasAllReceived: (List<Any>) -> Boolean = {
      val receivedEntities = it.filterIsInstance(ResponseComponentMessage::class.java)
              .map { it.component.entityId }
              .toSet()
      receivedEntities.containsAll(bestiasEids)
    }
    val props = AwaitResponseActor.props(wasAllReceived) {
      it.getAllReponses(ResponseComponentMessage::class)
              .map { ToClientEnvelope(accountId, it) }
              .forEach { sendToClient.tell(it, self) }
    }
    val responseAggregator = context.actorOf(props)

    val requestComponentMessage = RequestComponentMessage(responseAggregator)
    val broadcast = ComponentBroadcastEnvelope(requestComponentMessage)

    bestiasEids.forEach {
      val entityEnvelope = EntityEnvelope(it, broadcast)
      sendEntity.tell(entityEnvelope, self)
    }
  }

  companion object {
    const val NAME = "bestiaInfo"
  }
}