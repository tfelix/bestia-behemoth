package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Aspersio (`skills.yml` id 89): anoints an ally's weapon with [StatusEffectId.ASPERSIO]. Holy Water is its reagent. A
 * weapon holds one element, so the anointing lifts an endow off it.
 */
@Component
class Aspersio(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    ctx.world.applyStatusEffect(target, StatusEffectId.ASPERSIO.id, level = ctx.skillLevel.coerceIn(1, MAX_LEVEL))
    ctx.world.liftWeaponEndow(target)

    return null
  }

  private companion object {
    const val MAX_LEVEL = 3
  }
}
