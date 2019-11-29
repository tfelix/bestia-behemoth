package net.bestia.zoneserver.actor.entity.broadcast

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.component.Component
import net.bestia.zoneserver.entity.component.PlayerComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.map.MapService

data class BroadcastComponentMessage(
    val changedComponent: Component,
    val entity: Entity
)

private val LOG = KotlinLogging.logger { }

@Actor
class ClientComponentBroadcastActor(
    private val entityCollisionService: EntityCollisionService,
    private val messageApi: MessageApi
) : AbstractActor() {

  private val sendClient = SpringExtension.actorOf(context, SendToClientActor::class.java)

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(BroadcastComponentMessage::class.java, this::broadcastComponent)
        .build()
  }

  private fun broadcastComponent(msg: BroadcastComponentMessage) {
    broadcastToClientsReceivingMisc(msg)
    broadcastToClientsInRange(msg)
  }

  private fun broadcastToClientsReceivingMisc(msg: BroadcastComponentMessage): Set<Long> {
    // For each component there might be a factory/service whatever who decides which client gets
    // and update regardless of size
    return emptySet()
  }

  private fun broadcastToClientsInRange(msg: BroadcastComponentMessage) {
    val posComp = msg.entity.tryGetComponent(PositionComponent::class.java)
        ?: run {
          LOG.warn { "Position component of entity in message $msg not present. Can not send range update." }
          return
        }

    val updateRect = MapService.getUpdateRect(posComp.position)
    val activeIds = entityCollisionService.getAllCollidingEntityIds(updateRect)

    awaitEntityResponse(messageApi, context, activeIds) { entities ->
      val playerAccountIds = entities.all
          .mapNotNull { it.tryGetComponent(PlayerComponent::class.java)?.ownerAccountId }

      LOG.trace { "Sending component update ${msg.changedComponent.javaClass.simpleName} to: $playerAccountIds" }

      playerAccountIds.forEach {
        sendClient.tell(ClientEnvelope(it, msg.changedComponent), self)
      }
    }
  }

  companion object {
    const val NAME = "clientComponentBroadcast"
  }
}