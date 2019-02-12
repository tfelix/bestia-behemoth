package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.battle.StatusService
import net.bestia.zoneserver.entity.component.LevelComponent

@ActorComponent(LevelComponent::class)
class LevelComponentActor(
    levelComponent: LevelComponent,
    private val statusService: StatusService
) : ComponentActor<LevelComponent>(levelComponent) {

  override fun preStart() {
    fetchEntity { entity ->
      val newStatusComp = statusService.calculateStatusPoints(entity)
      context.parent.tell(newStatusComp, self)
    }
  }

  override fun onComponentChanged(oldComponent: LevelComponent, newComponent: LevelComponent) {
    if (oldComponent.level < newComponent.level) {
      fetchEntity { entity ->
        val newStatusComp = statusService.calculateStatusPoints(entity)
        context.parent.tell(newStatusComp, self)
      }
    }
  }
}