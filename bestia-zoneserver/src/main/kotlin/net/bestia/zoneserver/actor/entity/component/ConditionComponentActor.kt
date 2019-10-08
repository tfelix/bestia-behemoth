package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.battle.ConditionIncrements
import net.bestia.zoneserver.battle.RegenerationService
import net.bestia.zoneserver.entity.component.ConditionComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import java.time.Duration

@ActorComponent(ConditionComponent::class)
class ConditionComponentActor(
    conditionComponent: ConditionComponent,
    private val regenerationService: RegenerationService
) : ComponentActor<ConditionComponent>(conditionComponent) {

  private val currentIncrements = ConditionIncrements()
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
    val subscribeStatusComponent = SubscribeForComponentUpdates(StatusComponent::class.java, self)
    context.parent.tell(subscribeStatusComponent, self)
  }

  override fun createReceive(builder: ReceiveBuilder) {
    builder
        .matchEquals(ON_REGEN_TICK_MSG) { tickRegeneration() }
        .match(StatusComponent::class.java) { statusComponent = it }
  }

  private fun tickRegeneration() {
    statusComponent?.let {
      component = regenerationService.addIncrements(component, currentIncrements, it)
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
