package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.battle.RegenerationService
import net.bestia.zoneserver.entity.component.ConditionComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import java.time.Duration

open abstract class ConditionCommand : ComponentMessage<ConditionComponent>, EntityMessage {
  override val componentType: Class<out ConditionComponent>
    get() = ConditionComponent::class.java
}

// FIXME react on mana or hp changes
data class AddHp(override val entityId: Long, val hpDelta: Long) : ConditionCommand()

data class SetHp(override val entityId: Long, val hp: Long) : ConditionCommand()
data class AddMana(override val entityId: Long, val manaDelta: Long) : ConditionCommand()
data class SetMana(override val entityId: Long, val mana: Long) : ConditionCommand()

@ActorComponent(ConditionComponent::class)
class ConditionComponentActor(
    conditionComponent: ConditionComponent,
    private val regenerationService: RegenerationService
) : ComponentActor<ConditionComponent>(conditionComponent) {

  private val currentIncrements = RegenerationService.ConditionIncrements()
  // TODO This name confuses as its somehow not a component of its own
  private var statusComponent: StatusComponent? = null

  private val tick = context.system.scheduler().schedule(
      REGEN_TICK_INTERVAL,
      REGEN_TICK_INTERVAL,
      self,
      ON_REGEN_TICK_MSG,
      context.dispatcher(),
      null
  )

  override fun preStart() {
    createComponentUpdateSubscription(StatusComponent::class.java)
  }

  override fun createReceive(builder: ReceiveBuilder) {
    builder
        .matchEquals(ON_REGEN_TICK_MSG) { tickRegeneration() }
        .match(StatusComponent::class.java) { statusComponent = it }
  }

  private fun tickRegeneration() {
    statusComponent?.let {
      val conditionValues = regenerationService.addIncrements(
          component.conditionValues,
          it.statusBasedValues,
          currentIncrements
      )
      component = component.copy(conditionValues = conditionValues)
    }
  }

  override fun postStop() {
    tick.cancel()
  }

  companion object {
    private val REGEN_TICK_INTERVAL = Duration.ofMillis(RegenerationService.REGENERATION_TICK_RATE_MS)
    private const val ON_REGEN_TICK_MSG = "tickStatus"
    const val NAME = "conditionComponent"
  }
}
