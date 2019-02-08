package net.bestia.zoneserver.actor.bestia

import mu.KotlinLogging
import net.bestia.messages.bestia.BestiaActivateMessage
import net.bestia.zoneserver.actor.MessageApi
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.DynamicMessageRouterActor
import net.bestia.zoneserver.entity.PlayerEntityService

private val LOG = KotlinLogging.logger { }

/**
 * Upon receiving an activation request from this account we check if the
 * account is able to uses this bestia. It will then get activated and all
 * needed information about the newly activated bestia is send to the client.
 *
 * @author Thomas Felix
 */
@Actor
class ActivateBestiaActor(
    private val playerService: PlayerEntityService,
    private val messageApi: MessageApi
) : DynamicMessageRouterActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.match(BestiaActivateMessage::class.java, this::handleActivateBestia)
  }

  private fun handleActivateBestia(msg: BestiaActivateMessage) {
    awaitEntityResponse(messageApi, context, msg.entityId) {
      try {
        playerService.setActiveEntity(msg.accountId, it)
        LOG.debug("Activated player bestia from accId: {}, entityId: {}",
            msg.accountId,
            msg.entityId)

      } catch (ex: IllegalArgumentException) {
        LOG.warn { "Can not activate entity: $msg" }
      }
    }
  }

  companion object {
    const val NAME = "activateBestia"
  }
}
