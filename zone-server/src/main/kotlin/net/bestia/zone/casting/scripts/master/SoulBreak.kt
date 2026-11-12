package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import org.springframework.stereotype.Component

/**
 * Soul Break (`skills.yml` id 116): raw mana of `MATK * (120% + 40%/lv after Lv.1)`, raised by Arcane Mastery, less
 * MDEF. Raw mana has no element, so neither the element table nor a resistance weighs it. The target loses
 * `5% + 10%/lv after Lv.1` of its current mana; the caster gains none of it.
 */
@Component
class SoulBreak(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null
    val target = ctx.targetEntityId ?: return null
    val level = ctx.skillLevel.coerceIn(1, MAX_LEVEL)

    ctx.world.drainMana(target, DRAIN_PERCENT_AT_LV1 + DRAIN_PERCENT_PER_LEVEL * (level - 1))

    return HitDamage(damageOf(battle, level))
  }

  companion object {

    fun damageOf(battle: EntityBattleContext, level: Int): Int {
      val matk = battle.attacker.derivedStatusValues.matk + battle.weapon.matk
      val percent = MATK_PERCENT_AT_LV1 + MATK_PERCENT_PER_LEVEL * (level.coerceIn(1, MAX_LEVEL) - 1)
      val raw = matk * percent / 100f * (100 + battle.attacker.arcaneDamagePercent) / 100f

      return (raw - battle.defender.defense.magicDefense).toInt().coerceAtLeast(MIN_DAMAGE)
    }

    private const val MAX_LEVEL = 3
    private const val MATK_PERCENT_AT_LV1 = 120
    private const val MATK_PERCENT_PER_LEVEL = 40
    private const val DRAIN_PERCENT_AT_LV1 = 5
    private const val DRAIN_PERCENT_PER_LEVEL = 10
    private const val MIN_DAMAGE = 1
  }
}
