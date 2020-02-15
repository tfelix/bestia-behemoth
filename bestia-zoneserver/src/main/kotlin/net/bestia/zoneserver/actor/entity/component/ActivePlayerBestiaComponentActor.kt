package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.ActivePlayerBestiaComponent

@ActorComponent(ActivePlayerBestiaComponent::class)
class ActivePlayerBestiaComponentActor(
    activePlayerBestiaComponent: ActivePlayerBestiaComponent
) : ComponentActor<ActivePlayerBestiaComponent>(activePlayerBestiaComponent) {

  companion object {
    const val NAME = "activePlayerBestiaComponent"
  }
}