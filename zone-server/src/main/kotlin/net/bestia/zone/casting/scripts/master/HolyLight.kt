package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.Element
import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/** Holy Light (`skills.yml` id 86): a holy bolt of `MATK * (100% + 10%/lv)`, weighed by element, less MDEF. */
@Component
class HolyLight(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null

    return HitDamage(damageOf(battle, ctx.skillLevel, ignoresMagicDefense = false))
  }

  companion object {

    /** Shared with Master Turn Undead, whose blow is a Holy Light of the same level that pierces MDEF. */
    fun damageOf(battle: EntityBattleContext, level: Int, ignoresMagicDefense: Boolean): Int {
      val matk = battle.attacker.derivedStatusValues.matk + battle.weapon.matk
      val raw = matk * (100 + MATK_PERCENT_PER_LEVEL * level.coerceIn(1, MAX_LEVEL)) / 100f
      val holy = raw * ElementModifier.getModifierFloat(Element.HOLY, battle.defender.assumedElement)
      val defence = if (ignoresMagicDefense) 0 else battle.defender.defense.magicDefense

      return (holy - defence).toInt().coerceAtLeast(MIN_DAMAGE)
    }

    private const val MAX_LEVEL = 5
    private const val MATK_PERCENT_PER_LEVEL = 10
    private const val MIN_DAMAGE = 1
  }
}
