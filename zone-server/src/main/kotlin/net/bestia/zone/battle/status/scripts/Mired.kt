package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 220 (`QUAGMIRE`): stuck in the Wizard's mud. AGI and DEX fall by `10% + 20%/lv after
 * Lv.1`, at most 25% on a master or a player's bestia and 50% on anything wild. The bestia skill's mire is
 * [Quagmired].
 *
 * Its zone refreshes it on every area tick, so it ends soon after the bearer wades out.
 */
@Component
class Mired : StatusEffectScript {

  override val isPersisted: Boolean = false
  override val showsCountdown: Boolean = false

  /** A refresh does not run [onApply], so an AGI buff cast on a foe inside the mud is stripped here. */
  override val tickIntervalSeconds: Float = STRIP_SECONDS

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  override fun onApply(context: StatusEffectHookContext) {
    AGILITY_BUFFS.forEach { context.removeEffect(it) }
  }

  override fun onTick(context: StatusEffectTickContext) {
    AGILITY_BUFFS.forEach { context.strip(context.hostId, it) }
  }

  override fun apply(world: World, entityId: EntityId, context: StatusValueRecalcContext, effect: ActiveStatusEffect) {
    // Masters and the bestias they own both carry their player's account.
    val cut = cutPercent(effect.level, playerOwned = world.has(entityId, Account::class))

    context.agility = context.agility * (100 - cut) / 100
    context.dexterity = context.dexterity * (100 - cut) / 100
  }

  companion object {
    fun cutPercent(level: Int, playerOwned: Boolean): Int {
      val uncapped = FIRST_LEVEL_PERCENT + PERCENT_PER_LEVEL * (level.coerceIn(1, MAX_LEVEL) - 1)

      return uncapped.coerceAtMost(if (playerOwned) PLAYER_CAP_PERCENT else WILD_CAP_PERCENT)
    }

    /** Only Increase AGI raises AGI so far. */
    private val AGILITY_BUFFS = listOf(StatusEffectId.INCREASE_AGI)

    private const val FIRST_LEVEL_PERCENT = 10
    private const val PERCENT_PER_LEVEL = 20
    private const val MAX_LEVEL = 3
    private const val PLAYER_CAP_PERCENT = 25
    private const val WILD_CAP_PERCENT = 50
    private const val DURATION_SECONDS = 2.0
    private const val STRIP_SECONDS = 0.5f
  }
}
