package net.bestia.zone.casting.scripts

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.skill.persistence.Skill
import org.springframework.stereotype.Component

/**
 * Heal (`skills.yml` id 4), with the Ragnarok Online formula: `[(BaseLv + INT) / 8] * (4 + 8 * SkillLv)`.
 *
 * On an Undead-element foe it is a holy blow for half that amount, weighed by the element table and by no defence.
 * An Undead that is no foe, like an ally's bestia, is neither healed nor hurt.
 */
@Component
class Heal(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun costOf(skill: Skill, level: Int): SkillLevelCost {
    val rank = level.coerceIn(1, MAX_LEVEL)

    return SkillLevelCost.of(skill).copy(manaCost = BASE_MANA + MANA_PER_LEVEL * (rank - 1))
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null

    val levelAndInt = battle.attacker.level + battle.attacker.statusValues.intelligence
    val roHeal = (levelAndInt / 8) * (4 + 8 * battle.usedAttack.level)
    val targetElement = battle.defender.assumedElement

    if (targetElement.base != Element.UNDEAD) {
      return Heal((roHeal * battle.damageVariables.healMod).toInt())
    }

    val target = ctx.targetEntityId ?: return null
    if (ctx.world.isFoe(target)) {
      val holy = roHeal * ElementModifier.getModifierFloat(Element.HOLY, targetElement) / 2
      ctx.world.apply(target, HitDamage(holy.toInt()), HitKind.magic(Element.HOLY))
    }

    return null
  }

  private companion object {
    const val MAX_LEVEL = 10
    const val BASE_MANA = 13
    const val MANA_PER_LEVEL = 3
  }
}
