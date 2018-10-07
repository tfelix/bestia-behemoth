package net.bestia.zoneserver.actor.entity.component

import akka.actor.Cancellable
import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.entity.component.BattleComponent
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@Scope("prototype")
@HandlesComponent(BattleComponent::class)
class BattleComponentActor(
    battleComponent: BattleComponent
) : ComponentActor<BattleComponent>(battleComponent) {

  private var tick : Cancellable? = null

  override fun createReceive(builder: ReceiveBuilder) {
    builder.matchEquals(CHECK_DAMAGE_RETAIN_MSG) { removeExpiredDamageEntries() }
  }

  override fun onComponentChanged(oldComponent: BattleComponent, newComponent: BattleComponent) {
    tick?.cancel()
    tick = context.system().scheduler().schedule(
        Duration.ofSeconds(DAMAGE_RETAIN_TIME_S),
        Duration.ofSeconds(DAMAGE_RETAIN_TIME_S),
        self,
        CHECK_DAMAGE_RETAIN_MSG,
        context.dispatcher(),
        null
    )
  }

  private fun removeExpiredDamageEntries() {
    component.damageDealers.clear()
    updateEntitiesAboutComponentChanged()
  }

  companion object {
    private const val DAMAGE_RETAIN_TIME_S = 30 * 60.toLong()  // 30min
    private const val CHECK_DAMAGE_RETAIN_MSG = "checkdmgretain"
  }
}