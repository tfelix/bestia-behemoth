package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.mark.HuntersMarks
import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.identity.ecs.Account

/**
 * What every concealment shares: other players stop hearing of the bearer, staying hidden costs mana, and acting
 * gives it away. Not kept over a relog, because the visibility layer would not know of it.
 */
abstract class Concealment(private val visibility: EntityVisibility) : StatusEffectScript {

  override val isPersisted: Boolean = false
  override val tickIntervalSeconds: Float = 1f

  /** What the bearer may not do without giving itself away. */
  protected open val revealedBy: Set<EntityAction> = setOf(EntityAction.ATTACKED, EntityAction.CAST, EntityAction.USED_ITEM)

  override fun durationSeconds(level: Int): Double {
    return 60.0
  }

  /** Mana one tick of hiding costs. */
  protected open fun upkeep(effect: ActiveStatusEffect, mana: Mana): Int {
    return (mana.max * UPKEEP_PERCENT / 100).coerceAtLeast(1)
  }

  /** A drain in tenths of mana per second, so 2.5 a second costs 2 and 3 in turn. Counts ticks in `count`. */
  protected fun drainOfTenths(effect: ActiveStatusEffect, tenthsPerSecond: Int): Int {
    effect.count += 1

    return tenthsPerSecond * effect.count / 10 - tenthsPerSecond * (effect.count - 1) / 10
  }

  override fun onApply(context: StatusEffectHookContext) {
    val owner = context.world.get(context.hostId, Account::class)?.accountId
    val hunters = HuntersMarks.hunterAccountsOf(context.world, context.hostId)

    visibility.conceal(context.hostId, setOfNotNull(owner) + hunters)
  }

  override fun onRemove(context: StatusEffectHookContext, reason: RemovalReason) {
    visibility.reveal(context.hostId)
  }

  override fun onTick(context: StatusEffectTickContext) {
    val mana = context.world.get(context.hostId, Mana::class) ?: return
    val upkeep = upkeep(context.effect, mana)

    if (mana.current < upkeep) {
      context.removeSelf()
      return
    }
    mana.current -= upkeep
  }

  override fun onActed(context: StatusEffectHookContext, action: EntityAction) {
    if (action in revealedBy) {
      context.removeSelf()
    }
  }

  private companion object {
    const val UPKEEP_PERCENT = 1
  }
}
