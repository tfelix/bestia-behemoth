package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.identity.ecs.Account

/**
 * What [Hidden] and [Cloaked] share: other players stop hearing of the bearer, staying hidden costs mana, and
 * doing anything gives it away. Not kept over a relog, because the visibility layer would not know of it.
 */
abstract class Concealment(private val visibility: EntityVisibility) : StatusEffectScript {

  override val isPersisted: Boolean = false
  override val tickIntervalSeconds: Float = 1f

  override fun durationSeconds(level: Int): Double {
    return 60.0
  }

  override fun onApply(context: StatusEffectHookContext) {
    val owner = context.world.get(context.hostId, Account::class)?.accountId

    visibility.conceal(context.hostId, setOfNotNull(owner))
  }

  override fun onRemove(context: StatusEffectHookContext, reason: RemovalReason) {
    visibility.reveal(context.hostId)
  }

  override fun onTick(context: StatusEffectTickContext) {
    val mana = context.world.get(context.hostId, Mana::class) ?: return
    val upkeep = (mana.max * UPKEEP_PERCENT / 100).coerceAtLeast(1)

    if (mana.current < upkeep) {
      context.removeSelf()
      return
    }
    mana.current -= upkeep
  }

  override fun onActed(context: StatusEffectHookContext, action: EntityAction) {
    context.removeSelf()
  }

  private companion object {
    const val UPKEEP_PERCENT = 1
  }
}
