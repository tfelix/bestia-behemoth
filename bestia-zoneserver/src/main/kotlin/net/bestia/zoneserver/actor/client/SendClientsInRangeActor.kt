package net.bestia.zoneserver.actor.client

import mu.KotlinLogging
import net.bestia.entity.EntityService
import net.bestia.entity.component.PositionComponent
import net.bestia.messages.EntityJsonMessage
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import net.bestia.zoneserver.entity.PlayerEntityService
import net.bestia.zoneserver.map.MapService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/***
 * If a [EntityJsonMessage] is received by this actor it will check if the
 * given entity contains a [PositionComponent] and if it does it will
 * detect all active player entities in the update range of the game and forward
 * the message to them via a [SendToClientActor].
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class SendClientsInRangeActor(
        private val playerEntityService: PlayerEntityService,
        private val entityService: EntityService
) : BaseClientMessageRouteActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.match(EntityJsonMessage::class.java, this::sendToActiveInRange)
  }

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  /**
   * Sends to all active players in range. Maybe automatically detecting this
   * method by message type does not work out. Then we might need to create a
   * whole new actor only responsible for sending active range messages. Might
   * be better idea anyways.
   *
   * @param msg
   */
  private fun sendToActiveInRange(msg: EntityJsonMessage) {

    // Get position of the entity.
    val posComp = entityService.getComponent(msg.entityId,
            PositionComponent::class.java)

    if (!posComp.isPresent) {
      LOG.warn { "Position component of entity in message $msg not present. Can not send range update." }
      return
    }

    val pos = posComp.get().position
    val updateRect = MapService.getUpdateRect(pos)

    val activeIds = playerEntityService.getActiveAccountIdsInRange(updateRect)

    for (activeId in activeIds) {
      val newMsg = msg.createNewInstance(activeId!!)
      sendClient.tell(newMsg, self)
    }
  }

  companion object {
    const val NAME = "sendToClientsInRange"
  }
}

