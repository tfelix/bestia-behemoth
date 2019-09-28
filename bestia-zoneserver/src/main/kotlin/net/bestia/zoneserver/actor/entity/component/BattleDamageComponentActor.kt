package net.bestia.zoneserver.actor.entity.component

import akka.japi.pf.ReceiveBuilder
import net.bestia.zoneserver.actor.ActorComponent
import net.bestia.zoneserver.entity.component.BattleDamageComponent
import java.time.Duration

@ActorComponent(BattleDamageComponent::class)
class BattleDamageComponentActor(
    battleComponent: BattleDamageComponent
) : ComponentActor<BattleDamageComponent>(battleComponent) {

  init {
    context.system().scheduler().schedule(
        Duration.ofSeconds(CHECK_DAMAGE_RETAIN_S),
        Duration.ofSeconds(CHECK_DAMAGE_RETAIN_S),
        self,
        CHECK_DAMAGE_RETAIN_MSG,
        context.dispatcher(),
        null
    )
  }

  override fun createReceive(builder: ReceiveBuilder) {
    builder.matchEquals(CHECK_DAMAGE_RETAIN_MSG) { removeExpiredDamageEntries() }
  }

  private fun removeExpiredDamageEntries() {
    component = component.removeOutdatedEntries()
  }

  companion object {
    private const val CHECK_DAMAGE_RETAIN_S = 30L
    private const val CHECK_DAMAGE_RETAIN_MSG = "checkdmgretain"
  }
}