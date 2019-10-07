package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.battle.ConditionIncrements
import net.bestia.zoneserver.battle.RegenerationService
import net.bestia.zoneserver.battle.RegenerationService.Companion.REGENERATION_TICK_RATE_MS
import net.bestia.zoneserver.entity.component.StatusComponent
import net.bestia.zoneserver.inventory.InventoryService
import java.time.Duration

/**
 * The actor checks an entity with a status component attached and will
 * periodically calculate (usually every second) the hp and mana regeneration.
 * It will then update the current Mana and current HP values.
 *
 * @author Thomas Felix
 */
@ActorComponent(StatusComponent::class)
class StatusComponentActor(
    private val inventoryService: InventoryService,
    private val regenerationService: RegenerationService,
    component: StatusComponent
) : ComponentActor<StatusComponent>(component) {

  override fun onComponentChanged(oldComponent: StatusComponent, newComponent: StatusComponent) {
    fetchEntity {
      if (oldComponent.statusValues.strength != newComponent.statusValues.strength) {

        val newInventoryComp = inventoryService.updateMaxWeight(it)
        context.parent.tell(newInventoryComp, self)
      }

      announceComponentChange()
    }
  }

  companion object {
    const val NAME = "statusComponent"
  }
}
