package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 226 (`SHIELD_WALL`): the Knight's toggle. While a shield is held, `-15% - 5%/lv after Lv.1`
 * physical damage taken and slower on foot; at Lv.5 the attacker gets 10% of what the wall stopped. Dropped with the
 * shield.
 *
 * The cut is in the physical damage taken category of `DamageModifiers`, so it adds to Iron Skin and multiplies with
 * Magic Armor.
 */
@Component
class ShieldWalled : StatusEffectScript {

  /** A stance, not a spell: a relog ends it. */
  override val isPersisted: Boolean = false
  override val showsCountdown: Boolean = false
  override val tickIntervalSeconds: Float = 1f

  override fun durationSeconds(level: Int): Double {
    return Double.POSITIVE_INFINITY
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    if (context.shieldArmorType == null) return

    context.addDamageTaken(cutPercent(level), form = HitForm.PHYSICAL)
    context.speed *= (100 - slowPercent(level)) / 100f
  }

  override fun onTick(context: StatusEffectTickContext) {
    if (context.world.get(context.hostId, CombatStats::class)?.values?.shieldArmorType == null) {
      context.removeSelf()
    }
  }

  override fun onDamageTaken(context: StatusEffectHookContext, hit: IncomingDamage.DamageAmount): Int {
    val level = context.effect.level
    if (level < RETURNS_FROM_LEVEL || hit.kind.form != HitForm.PHYSICAL || hit.sourceEntityId == context.hostId) {
      return hit.amount
    }
    val combat = context.world.get(context.hostId, CombatStats::class)?.values ?: return hit.amount
    if (combat.shieldArmorType == null) return hit.amount

    val physicalCut = combat.damageModifiers.takenByForm[HitForm.PHYSICAL] ?: 0
    val stopped = stoppedByWall(hit.amount, cutPercent(level), physicalCut)
    context.passOn(hit.sourceEntityId, stopped * RETURNED_PERCENT / 100, from = context.hostId)

    return hit.amount
  }

  companion object {
    private const val MAX_LEVEL = 5
    private const val RETURNS_FROM_LEVEL = 5
    const val RETURNED_PERCENT = 10

    fun cutPercent(level: Int): Int {
      return 10 + 5 * level.coerceIn(1, MAX_LEVEL)
    }

    fun slowPercent(level: Int): Int {
      return when (level.coerceIn(1, MAX_LEVEL)) {
        1 -> 50
        2 -> 40
        else -> 30
      }
    }

    /**
     * What the wall took off a hit that [landed] after the whole physical category, [physicalCutPercent], of which
     * the wall's [wallCutPercent] is a part: categories add, so the wall stopped its share of the hit before them.
     */
    fun stoppedByWall(landed: Int, wallCutPercent: Int, physicalCutPercent: Int): Int {
      if (physicalCutPercent >= 100) return 0

      return landed * wallCutPercent / (100 - physicalCutPercent)
    }
  }
}
