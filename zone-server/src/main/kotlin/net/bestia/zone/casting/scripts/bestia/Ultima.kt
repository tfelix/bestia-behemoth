package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.TrueDamage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Takes a quarter of its target's health whatever the caster's power or the target's guard. Against a boss, whose
 * pool is many times a player's, only a twentieth.
 */
@Component
class Ultima(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null
    val boss = ctx.targetEntityId?.let { ctx.world.natureOf(it) }?.boss == true
    val percent = if (boss) BOSS_PERCENT else PERCENT

    return TrueDamage((battle.defender.maxHealth * percent / 100).coerceAtLeast(1))
  }

  private companion object {
    const val PERCENT = 25
    const val BOSS_PERCENT = 5
  }
}
