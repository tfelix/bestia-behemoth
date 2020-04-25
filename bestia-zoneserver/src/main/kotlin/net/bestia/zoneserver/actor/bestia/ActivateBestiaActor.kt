package net.bestia.zoneserver.actor.bestia

import mu.KotlinLogging
import net.bestia.messages.bestia.BestiaSetActive
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.entity.commands.AddComponentCommand
import net.bestia.zoneserver.actor.entity.commands.DeleteComponentCommand
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.entity.component.ComponentEnvelope
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.entity.PlayerEntityService
import net.bestia.zoneserver.entity.component.ActivePlayerBestiaComponent

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
) : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.matchRedirect(BestiaSetActive::class.java, this::handleActivateBestia)
  }

  private fun handleActivateBestia(msg: BestiaSetActive) {
    val playerEntityIds = playerService.getPlayerEntities(msg.accountId) - setOf(msg.playerBestiaId)

    val deleteMsg = DeleteComponentCommand(
        entityId = 0,
        componentClass = ActivePlayerBestiaComponent::class.java
    )
    val componentEnvelope = ComponentEnvelope(ActivePlayerBestiaComponent::class.java, deleteMsg)
    val entityEnvelope = EntityEnvelope(0, componentEnvelope)

    playerEntityIds.forEach {
      messageApi.send(entityEnvelope.copy(entityId = it))
    }

    val addMsg = AddComponentCommand(ActivePlayerBestiaComponent(msg.entityId))
    val componentAddEnvelope = ComponentEnvelope(ActivePlayerBestiaComponent::class.java, addMsg)
    val entityActiveEnvelope = EntityEnvelope(msg.entityId, componentAddEnvelope)

    LOG.debug { "Activated player bestia from accId: ${msg.accountId}, entityId: ${msg.entityId}" }
    messageApi.send(entityActiveEnvelope)
  }

  companion object {
    const val NAME = "activateBestia"
  }
}
