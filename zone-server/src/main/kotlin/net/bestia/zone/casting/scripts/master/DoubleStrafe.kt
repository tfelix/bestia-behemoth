package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Double Strafe (`skills.yml` id 155): two shots at a foe for `100% + 10%/lv` ATK each, both from the one arrow it
 * takes out of the quiver.
 */
@Component
class DoubleStrafe(
  losService: LineOfSightService,
  ammo: AmmoSupply,
) : BowSkillStrategy(losService, ammo) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) && ctx.world.isFoe(target)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val shot = nockArrow(ctx) ?: return null

    repeat(SHOTS) { ctx.world.strike(target, shot, atkPercent(ctx.skillLevel)) }

    return null
  }

  companion object {
    private const val MAX_LEVEL = 10
    const val SHOTS = 2

    fun atkPercent(level: Int): Int {
      return 100 + 10 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
