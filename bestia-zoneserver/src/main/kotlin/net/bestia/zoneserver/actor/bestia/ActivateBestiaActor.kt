package net.bestia.zoneserver.actor.bestia

import mu.KotlinLogging
import net.bestia.messages.bestia.BestiaActivateMessage
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.actor.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Upon receiving an activation request from this account we check if the
 * account is able to uses this bestia. It will then get activated and all
 * needed information about the newly activated bestia is send to the client.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class ActivateBestiaActor(
        private val playerService: PlayerEntityService,
        private val messageApi: MessageApi
) : BaseClientMessageRouteActor() {

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
