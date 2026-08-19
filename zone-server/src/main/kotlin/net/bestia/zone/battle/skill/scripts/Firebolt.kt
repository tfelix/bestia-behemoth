package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.skill.BasicMagicSkillStrategy
import net.bestia.zone.battle.skill.SkillContext
import org.springframework.stereotype.Component

/**
 * Firebolt (`skills.yml` id 5): a channelled single-target fire bolt.
 *
 * The script computes its own damage, exactly like [Heal] computes its own healing -
 * [net.bestia.zone.battle.damage.MagicDamageCalculator] is still unimplemented, so the formula lives here.
 *
 * Because the cast is channelled, the range and line-of-sight check inherited from
 * [BasicMagicSkillStrategy] runs when the cast *completes*, so walking out of range or breaking line of sight
 * mid-cast makes the bolt fizzle.
 */
@Component
class Firebolt(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null

    val attacker = battle.attacker
    val base = (attacker.level / 4 + attacker.statusValues.intelligence) * battle.usedAttack.level
    val matk = attacker.derivedStatusValues.matk + battle.weapon.matk
    val mitigated = base + matk - battle.defender.defense.magicDefense

    // A landed bolt always chips at least a point off, however tanky the target.
    return HitDamage(mitigated.coerceAtLeast(MIN_DAMAGE))
  }

  companion object {
    private const val MIN_DAMAGE = 1
  }
}
