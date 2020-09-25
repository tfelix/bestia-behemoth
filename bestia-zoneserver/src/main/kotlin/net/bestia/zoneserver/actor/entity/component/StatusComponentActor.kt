package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.AllEntitiesStatusComponentFactory
import net.bestia.zoneserver.entity.component.EquipComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.StatusComponent

@ActorComponent(StatusComponent::class)
class StatusComponentActor(
    component: StatusComponent,
    val statusComponentFactory: AllEntitiesStatusComponentFactory
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
      component = statusComponentFactory.buildComponent(it)
    }
  }

  companion object {
    const val NAME = "statusComponent"
  }
}
