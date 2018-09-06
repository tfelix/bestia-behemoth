package net.bestia.zoneserver.actor.bestia

import mu.KotlinLogging
import net.bestia.messages.bestia.BestiaInfoRequestMessage
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.entity.SendToEntityActor
import net.bestia.zoneserver.actor.entity.component.RequestAllComponentMessage
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

  override fun createReceive(builder: BuilderFacade) {
    builder.match(BestiaInfoRequestMessage::class.java, this::handleInfoRequest)
    builder.noRedirect.match(ResponseComponentMessage::class.java, this::handleComponentResponse)
  }

  private fun handleComponentResponse(msg: ResponseComponentMessage<*>) {
    // TODO I dont think thats the best way of handling the response.
  }

  private fun handleInfoRequest(msg: BestiaInfoRequestMessage) {
    LOG.debug(String.format("Received: %s", msg.toString()))

    val accId = msg.accountId
    val bestiasEids = playerEntityService.getPlayerEntities(accId).map { it.id }.toSet()

    val requestComponentMessage = RequestAllComponentMessage(accId, self)
    bestiasEids.forEach {
      val entityEnvelope = EntityEnvelope(it, requestComponentMessage)
      sendEntity.tell(entityEnvelope, self)
    }
  }

  companion object {
    const val NAME = "bestiaInfo"
  }
}