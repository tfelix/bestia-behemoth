package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.entity.SendToEntityActor
import net.bestia.zoneserver.entity.EntityCollisionService
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.component.ScriptComponent

@ActorComponent(PositionComponent::class)
class PositionComponentActor(
    positionComponent: PositionComponent,
    private val entityCollisionService: EntityCollisionService
) : ComponentActor<PositionComponent>(positionComponent) {

  val sendToEntityActor = SpringExtension.actorOf(context, SendToEntityActor::class.java)

  override fun onComponentChanged(oldComponent: PositionComponent, newComponent: PositionComponent) {
    val previousCollisions = entityCollisionService.getAllCollidingEntityIds(oldComponent.shape)
    val newCollisions = entityCollisionService.getAllCollidingEntityIds(newComponent.shape)

    val collisionsLeft = previousCollisions - newCollisions
    val collisionsEntered = newCollisions - previousCollisions

    val leftTrigger = ComponentEnvelope(
        ScriptComponent::class.java,
        ScriptTriggerAreaLeft(component.entityId)
    )
    collisionsLeft.map { EntityEnvelope(it, leftTrigger) }.forEach {
      sendToEntityActor.tell(it, self)
    }

    val enteredTrigger = ComponentEnvelope(
        ScriptComponent::class.java,
        ScriptTriggerAreaEntered(component.entityId)
    )
    collisionsEntered.map { EntityEnvelope(it, enteredTrigger) }.forEach {
      sendToEntityActor.tell(it, self)
    }

    announceComponentChange()
  }
}