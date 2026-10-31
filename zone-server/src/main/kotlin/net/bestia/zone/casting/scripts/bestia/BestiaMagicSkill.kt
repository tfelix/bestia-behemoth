package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * The bolt and ultimate archetypes: the Firebolt formula `((lv/4 + INT) * skillLv + MATK) * coef - SoftMDEF`
 * from `bestia_blueprint.yaml`, weighed by the skill's element against the target's.
 */
@Component
class BestiaMagicSkill(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null

    val raw = BestiaSpellPower.of(ctx) * ctx.attackCoefficient
    val elementMod = ElementModifier.getModifierFloat(battle.usedAttack.attackElement, battle.defender.assumedElement)
    val mitigated = raw * elementMod - battle.defender.defense.magicDefense

    // A landed bolt always chips at least a point off, as Firebolt does.
    return HitDamage(mitigated.toInt().coerceAtLeast(MIN_DAMAGE))
  }

  private companion object {
    const val MIN_DAMAGE = 1
  }
}
