package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.VisualComponent

@ActorComponent(VisualComponent::class)
class VisualComponentActor(
    visualComponent: VisualComponent
) : ComponentActor<VisualComponent>(visualComponent) {

  override fun onComponentChanged(oldComponent: VisualComponent, newComponent: VisualComponent) {

    // TODO This is tricky. If the entity is now invisible only play the make invis animation
    // and then stop all updates to this entity like e.g. position.

    announceComponentChange()
  }
}