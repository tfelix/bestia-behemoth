package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.battle.StatusService
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
    private val statusService: StatusService,
    private val inventoryService: InventoryService,
    component: StatusComponent
) : ComponentActor<StatusComponent>(component) {

  private val tick = context.system.scheduler().schedule(
      REGEN_TICK_INTERVAL,
      REGEN_TICK_INTERVAL,
      self,
      ON_REGEN_TICK_MSG,
      context.dispatcher(),
      null
  )

  private var manaIncrement = 0f
  private var healthIncrement = 0f

  override fun createReceive(builder: ReceiveBuilder) {
    builder.matchEquals(ON_REGEN_TICK_MSG) { onRegenTick() }
  }

  private fun onRegenTick() {
    fetchEntity { entity ->
      healthIncrement += statusService.getHealthTick(entity)
      manaIncrement += statusService.getManaTick(entity)

      val condValues = entity.getComponent(StatusComponent::class.java).conditionValues
      if (healthIncrement > 1) {
        val hpRound = healthIncrement.toInt()
        healthIncrement -= hpRound.toFloat()
        condValues.addHealth(hpRound)
      }

      if (manaIncrement > 1) {
        val manaRound = manaIncrement.toInt()
        manaIncrement -= manaRound.toFloat()
        condValues.addMana(manaRound)
      }
    }
  }

  override fun onComponentChanged(oldComponent: StatusComponent, newComponent: StatusComponent) {
    if(oldComponent.statusValues.strength != newComponent.statusValues.strength) {
      fetchEntity {
        val newInventoryComp = inventoryService.updateMaxWeight(it)
        context.parent.tell(newInventoryComp, self)
      }
    }

    announceComponentChange()
  }

  override fun postStop() {
    tick.cancel()
  }

  companion object {
    private val REGEN_TICK_INTERVAL = Duration.ofMillis(StatusService.REGENERATION_TICK_RATE_MS)
    private const val ON_REGEN_TICK_MSG = "tickStatus"
    const val NAME = "statusComponent"
  }
}
