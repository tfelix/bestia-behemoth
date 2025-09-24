package net.bestia.zone.message.processor.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.message.SelectEntityCMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.ecs.session.EntityNotOwnedSessionException
import org.springframework.stereotype.Component

/**
 * Selects the entity the player wants to focus on. This means this entity will
 * be used as reference point for the player updates.
 */
@Component
class SelectEntityHandler(
  private val connectionInfoService: ConnectionInfoService,
) : InMessageProcessor.IncomingMessageHandler<SelectEntityCMSG> {
  override val handles = SelectEntityCMSG::class

  override fun handle(msg: SelectEntityCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    try {
      connectionInfoService.activateEntity(msg.playerId, msg.entityId)
    } catch (e: EntityNotOwnedSessionException) {
      LOG.warn { "Can not select entity, no entity ${msg.entityId} found for player ${msg.playerId}" }
      return false
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
