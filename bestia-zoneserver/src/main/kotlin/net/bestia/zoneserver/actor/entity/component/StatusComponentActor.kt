package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.EquipComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import net.bestia.zoneserver.status.StatusValueService

@ActorComponent(StatusComponent::class)
class StatusComponentActor(
    component: StatusComponent,
    val statusValueService: StatusValueService
) : ComponentActor<StatusComponent>(component) {

  override fun createReceive(builder: ReceiveBuilder) {
    builder
        .match(LevelComponent::class.java) { calculateStatusValues() }
        .match(EquipComponent::class.java) { calculateStatusValues() }
  }

  override fun preStart() {
    createComponentUpdateSubscription(LevelComponent::class.java)
    createComponentUpdateSubscription(EquipComponent::class.java)
  }

  private fun calculateStatusValues() {
    requestOwnerEntity {
      component = statusValueService.buildStatusComponent(it)
    }
  }

  companion object {
    const val NAME = "statusComponent"
  }
}
