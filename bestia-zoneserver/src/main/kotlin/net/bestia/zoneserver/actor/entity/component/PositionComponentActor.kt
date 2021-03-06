package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import mu.KotlinLogging
import net.bestia.messages.entity.EntityMessage
import net.bestia.model.geometry.Vec3
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.entity.transmit.InRangeTransmitFilter
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.component.PositionComponent

data class SetPositionToAbsolute(
    override val entityId: Long,
    val position: Vec3
) : EntityMessage, ComponentMessage<PositionComponent> {
  override val componentType = PositionComponent::class.java
}

data class SetPositionToOffset(
    override val entityId: Long,
    val offset: Vec3
) : EntityMessage, ComponentMessage<PositionComponent> {
  override val componentType = PositionComponent::class.java
}

private val LOG = KotlinLogging.logger { }

@ActorComponent(
    component = PositionComponent::class
)
class PositionComponentActor(
    positionComponent: PositionComponent,
    private val entityCollisionService: EntityCollisionService
) : ComponentActor<PositionComponent>(positionComponent) {

  override fun createReceive(builder: ReceiveBuilder) {
    builder.match(SetPositionToAbsolute::class.java) { setPosition(it.position) }
    builder.match(SetPositionToOffset::class.java) { setPosition(component.position + it.offset) }
  }

  private fun setPosition(newPos: Vec3) {
    LOG.trace { "setPosition: $newPos" }
    component = component.copy(shape = component.shape.moveTo(newPos))
  }

  override fun onComponentChanged(oldComponent: PositionComponent, newComponent: PositionComponent) {
    entityCollisionService.updateEntityCollision(newComponent.entityId, newComponent.shape)
  }
}