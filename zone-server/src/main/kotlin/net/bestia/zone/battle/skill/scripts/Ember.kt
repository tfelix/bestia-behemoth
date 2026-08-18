package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.AreaEffectResult
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.skill.BasicMagicSkillStrategy
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * Ember (`skills.yml` id 1000): sets a patch of ground alight, which burns everything standing in it
 * every 1.2 seconds for 9.6 seconds - eight ticks.
 *
 * Declared `NO_DAMAGE` in the catalogue, which here means "script-driven" rather than "harmless", the
 * same way [Firebolt] is. The per-tick number is computed once, at cast time, from the caster's stats:
 * a patch has no single defender whose magic defence could be subtracted, so unlike [Firebolt] the
 * damage is unmitigated. Working defence into it needs the mitigation to move to the point of
 * application in [net.bestia.zone.ecs.battle.effects.AreaEffectSystem], which is a change to every
 * area effect rather than to this one.
 *
 * The radius comes from the catalogue rather than from a constant here, so the circle the client draws
 * while aiming and the square the server damages are sized by one number. They are still a circle and
 * a square: at radius 1 the indicator is inscribed in the 3x3 that actually burns.
 */
@Component
class Ember(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun doAttack(ctx: BattleContext): Damage {
    return when (ctx) {
      is GroundBattleContext -> ember(ctx)
      is EntityBattleContext -> Miss
    }
  }

  override fun isAttackPossible(ctx: BattleContext): Boolean {
    return if (ctx is GroundBattleContext) {
      super.isAttackPossible(ctx)
    } else {
      false
    }
  }

  private fun ember(ctx: GroundBattleContext): Damage {
    val attacker = ctx.attacker
    val base = attacker.level / 8 + attacker.statusValues.intelligence / 2
    val matk = attacker.derivedStatusValues.matk + ctx.weapon.matk
    val perTick = (base + matk / 4) * ctx.usedAttack.level

    return AreaEffectResult(
      radiusTiles = ctx.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
      damagePerTick = perTick.coerceAtLeast(MIN_DAMAGE_PER_TICK),
      tickIntervalSeconds = TICK_INTERVAL_SECONDS,
      durationSeconds = DURATION_SECONDS,
      visualId = PATCH_VISUAL_ID
    )
  }

  companion object {
    private const val TICK_INTERVAL_SECONDS = 1.2f
    private const val DURATION_SECONDS = 9.6f
    private const val MIN_DAMAGE_PER_TICK = 1

    /** Only reached if the catalogue entry loses its `aoeRadius`, which `Skill`'s own check forbids. */
    private const val DEFAULT_RADIUS_TILES = 1L

    /** `effect_id` of `1_ember_patch.tres` in the client's `Game/Entity/Visual/EffectVisual/DB`. */
    private const val PATCH_VISUAL_ID = 1L
  }
}
