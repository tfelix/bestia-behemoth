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
    val statusCompSubscribeMsg = SubscribeForComponentUpdates(StatusComponent::class.java, self)
    context.parent.tell(statusCompSubscribeMsg, self)
    val levelCompSubscribeMsg = SubscribeForComponentUpdates(LevelComponent::class.java, self)
    context.parent.tell(levelCompSubscribeMsg, self)
  }

  override fun createReceive(builder: ReceiveBuilder) {
    builder
        .match(StatusComponent::class.java) { recalculateMaxWeight() }
        .match(LevelComponent::class.java) { recalculateMaxWeight() }
  }

  private fun recalculateMaxWeight() {
    fetchEntity {
      component = inventoryService.updateMaxWeight(it)
    }
  }
}