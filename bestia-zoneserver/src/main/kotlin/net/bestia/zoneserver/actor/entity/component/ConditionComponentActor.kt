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

  private val tick = context.system.scheduler().schedule(
      REGEN_TICK_INTERVAL,
      REGEN_TICK_INTERVAL,
      self,
      ON_REGEN_TICK_MSG,
      context.dispatcher(),
      null
  )

  override fun createReceive(builder: ReceiveBuilder) {
    builder.matchEquals(ON_REGEN_TICK_MSG) { tickRegeneration() }
  }

  private fun tickRegeneration() {
    fetchEntity { entity ->
      val statusComponent = entity.getComponent(StatusComponent::class.java)
      regenerationService.addIncrements(currentIncrements, statusComponent)
      component = regenerationService.transferIncrementsToCondition(currentIncrements, component)
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
