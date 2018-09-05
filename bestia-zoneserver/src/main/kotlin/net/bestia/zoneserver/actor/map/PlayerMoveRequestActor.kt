package net.bestia.zoneserver.actor.map

import net.bestia.entity.EntityService
import net.bestia.entity.component.MoveComponent
import net.bestia.messages.entity.EntityMoveRequestMessage
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * Incoming player requests to move a bestia must be send towards the actor of
 * the entity which will handle the movement. Message must be wrapped in an
 * component envelope in order to get delivered.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class PlayerMoveRequestActor(
        private val entityService: EntityService
) : BaseClientMessageRouteActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder.match(EntityMoveRequestMessage::class.java, this::handleMoveRequest)
  }

  private fun handleMoveRequest(msg: EntityMoveRequestMessage) {
    val mc = entityService.getComponentOrCreate(msg.entityId, MoveComponent::class.java)
    mc.setPath(msg.path)
    entityService.updateComponent(mc)
  }
}