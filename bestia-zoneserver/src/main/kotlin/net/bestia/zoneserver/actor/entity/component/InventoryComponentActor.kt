package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.InventoryComponent

@ActorComponent(InventoryComponent::class)
class InventoryComponentActor(
    inventoryComponent: InventoryComponent
) : ComponentActor<InventoryComponent>(inventoryComponent) {

  override fun onComponentChanged(oldComponent: InventoryComponent, newComponent: InventoryComponent) {
    announceComponentChange()
  }
}