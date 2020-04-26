package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.InventoryComponent
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import net.bestia.zoneserver.inventory.InventoryService

@ActorComponent(InventoryComponent::class)
class InventoryComponentActor(
    inventoryComponent: InventoryComponent,
    private val inventoryService: InventoryService
    ) : ComponentActor<InventoryComponent>(inventoryComponent) {

  override fun preStart() {
    createComponentUpdateSubscription(LevelComponent::class.java)
    createComponentUpdateSubscription(LevelComponent::class.java)
  }

  override fun createReceive(builder: ReceiveBuilder) {
    builder
        .match(StatusComponent::class.java) { recalculateMaxWeight() }
        .match(LevelComponent::class.java) { recalculateMaxWeight() }
  }

  private fun recalculateMaxWeight() {
    requestOwnerEntity {
      component = inventoryService.updateMaxWeight(it)
    }
  }
}