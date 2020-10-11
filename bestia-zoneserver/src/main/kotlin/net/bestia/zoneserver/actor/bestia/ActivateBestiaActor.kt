package net.bestia.zoneserver.actor.bestia

import mu.KotlinLogging
import net.bestia.messages.bestia.BestiaSetActive
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.entity.component.UpdateComponent
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.PlayerEntityService
import net.bestia.zoneserver.entity.component.MetadataComponent

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
    playerService.getActivePlayerEntityId(msg.accountId)?.let { currentActiveId ->
      awaitEntityResponse(messageApi, context, currentActiveId) {
        val currentMetaData = it.tryGetComponent(MetadataComponent::class.java)
            ?: MetadataComponent(entityId = it.id)
        val updateMsg = UpdateComponent(
            currentMetaData.copyWithoutKey(MetadataComponent.PLAYER_IS_ACTIVE)
        )
        messageApi.send(updateMsg)
      }
    }

    awaitEntityResponse(messageApi, context, msg.entityId) {
      val currentMetaData = it.tryGetComponent(MetadataComponent::class.java)
          ?: MetadataComponent(entityId = it.id)
      val updatedComponent = UpdateComponent(
          currentMetaData.copyWith(MetadataComponent.PLAYER_IS_ACTIVE, true)
      )

      LOG.debug { "Activated Player Bestia from accId: ${msg.accountId}, entityId: ${msg.entityId}" }
      messageApi.send(updatedComponent)
    }
  }

  companion object {
    const val NAME = "activateBestia"
  }
}
