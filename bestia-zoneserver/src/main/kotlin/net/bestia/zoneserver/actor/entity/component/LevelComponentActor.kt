package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.LevelComponent

@ActorComponent(LevelComponent::class)
class LevelComponentActor(
    levelComponent: LevelComponent
) : ComponentActor<LevelComponent>(levelComponent) {

  companion object {
    const val NAME = "levelComponent"
  }
}