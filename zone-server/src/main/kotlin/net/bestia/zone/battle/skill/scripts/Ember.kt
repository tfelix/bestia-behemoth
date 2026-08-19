package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.skill.BasicMagicSkillStrategy
import net.bestia.zone.battle.skill.SkillContext
import net.bestia.zone.ecs.battle.effects.AreaEffect
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * Ember (`skills.yml` id 1000): sets a patch of ground alight, which burns everything standing in it every
 * 1.2 seconds for 9.6 seconds - eight ticks.
 *
 * The per-tick number is computed once, at cast time, from the caster's stats: a patch has no single
 * defender whose magic defence could be subtracted, so unlike [Firebolt] the damage is unmitigated. Working
 * defence into it needs the mitigation to move to the point of application in
 * [net.bestia.zone.ecs.battle.effects.AreaEffectSystem], which is a change to every area effect rather than
 * to this one.
 *
 * The radius comes from the catalogue rather than from a constant here, so the circle the client draws while
 * aiming and the square the server damages are sized by one number. They are still a circle and a square: at
 * radius 1 the indicator is inscribed in the 3x3 that actually burns.
 */
@Component
class Ember(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is GroundBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val ground = ctx.requireGroundContext()
    val attacker = ground.attacker

    val base = attacker.level / 8 + attacker.statusValues.intelligence / 2
    val matk = attacker.derivedStatusValues.matk + ground.weapon.matk
    val perTick = ((base + matk / 4) * ground.usedAttack.level).coerceAtLeast(MIN_DAMAGE_PER_TICK)

    ctx.world.spawnAreaEffect(
      centre = ground.targetPosition,
      visualId = PATCH_VISUAL_ID,
      effect = AreaEffect.lasting(
        casterId = ctx.casterId,
        skillId = ctx.skillId,
        skillLevel = ctx.skillLevel,
        radiusTiles = ground.usedAttack.aoeRadius?.roundToLong() ?: DEFAULT_RADIUS_TILES,
        damagePerTick = perTick,
        tickIntervalSeconds = TICK_INTERVAL_SECONDS,
        durationSeconds = DURATION_SECONDS,
        hitsCaster = true
      )
    )

    // The patch is the whole effect; there is no entity to float a number over.
    return null
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
