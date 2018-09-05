package net.bestia.zoneserver.actor.bestia

import mu.KotlinLogging
import net.bestia.messages.bestia.BestiaInfoMessage
import net.bestia.messages.bestia.BestiaInfoRequestMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
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

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(builder: BuilderFacade) {
    builder.match(BestiaInfoRequestMessage::class.java, this::handleInfoRequest)
  }

  private fun handleInfoRequest(msg: BestiaInfoRequestMessage) {
    LOG.debug(String.format("Received: %s", msg.toString()))

    val accId = msg.accountId

    val bestiasEids = playerEntityService.getPlayerEntities(accId).map { it.id }.toSet()
    val masterEid = playerEntityService.getMasterEntity(accId).map { it.id }.orElse(0L)

    val bimsg = BestiaInfoMessage(accId, masterEid, bestiasEids)
    sendClient.tell(bimsg, self)
  }

  companion object {
    const val NAME = "bestiaInfo"
  }
}