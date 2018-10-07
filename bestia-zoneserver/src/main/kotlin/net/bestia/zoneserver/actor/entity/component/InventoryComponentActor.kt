package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.entity.component.InventoryComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
@HandlesComponent(InventoryComponent::class)
class InventoryComponentActor(
    inventoryComponent: InventoryComponent
) : ComponentActor<InventoryComponent>(inventoryComponent) {
  override fun createReceive(builder: ReceiveBuilder) {

  }
}