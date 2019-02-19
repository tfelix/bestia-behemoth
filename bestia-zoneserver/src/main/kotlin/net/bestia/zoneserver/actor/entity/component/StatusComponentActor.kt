package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.model.entity.REGENERATION_TICK_RATE_MS
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.battle.MobStatusService
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
    private val statusService: MobStatusService,
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
  private var staminaIncrement = 0f

  override fun createReceive(builder: ReceiveBuilder) {
    builder.matchEquals(ON_REGEN_TICK_MSG) { onRegenTick() }
  }

  private fun onRegenTick() {
    healthIncrement = component.statusBasedValues.hpRegenRate * REGENERATION_TICK_RATE_MS / 1000
    manaIncrement = component.statusBasedValues.manaRegenRate * REGENERATION_TICK_RATE_MS / 1000
    staminaIncrement = component.statusBasedValues.staminaRegenRate * REGENERATION_TICK_RATE_MS / 1000

    val condValues = component.conditionValues
    val hpRound = healthIncrement.toInt()
    healthIncrement -= hpRound.toFloat()

    val manaRound = manaIncrement.toInt()
    manaIncrement -= manaRound.toFloat()

    val staminaRound = staminaIncrement.toInt()
    staminaIncrement -= staminaRound.toFloat()

    val updatedConditionValues = condValues.copy(
        currentMana = condValues.currentMana + manaRound,
        currentHealth = condValues.currentHealth + hpRound,
        currentStamina = condValues.currentStamina + staminaRound
    )

    component = component.copy(conditionValues = updatedConditionValues)
  }

  override fun onComponentChanged(oldComponent: StatusComponent, newComponent: StatusComponent) {
    fetchEntity {
      if (oldComponent.statusValues.strength != newComponent.statusValues.strength) {

        val newInventoryComp = inventoryService.updateMaxWeight(it)
        context.parent.tell(newInventoryComp, self)
      }

      announceComponentChange()
    }
  }

  override fun postStop() {
    tick.cancel()
  }

  companion object {
    private val REGEN_TICK_INTERVAL = Duration.ofMillis(REGENERATION_TICK_RATE_MS)
    private const val ON_REGEN_TICK_MSG = "tickStatus"
    const val NAME = "statusComponent"
  }
}
