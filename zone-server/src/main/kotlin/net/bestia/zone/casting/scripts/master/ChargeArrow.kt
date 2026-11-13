package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Charge Arrow (`skills.yml` id 157): one shot at a foe for 150% ATK. If it lands, it pushes the foe 6 m away from the
 * Hunter.
 */
@Component
class ChargeArrow(
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

    if (ctx.world.strike(target, shot, ATK_PERCENT).landed()) {
      ctx.world.knockBack(target, ctx.battle.attacker.position, KNOCKBACK_TILES)
    }

    return null
  }

  companion object {
    const val ATK_PERCENT = 150
    const val KNOCKBACK_TILES = 6
  }
}
