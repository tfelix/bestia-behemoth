package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import org.springframework.stereotype.Component

/**
 * One blow that pours all mana into it (Ragnarok Online's Guillotine Fist): only from a berserk caster at least
 * half full of mana, `ATK * (4 + mana spent / 10)`. It ends the berserk and seals the mana pool for five minutes.
 */
@Component
class UltimaFist(
  private val attackStrategies: AttackStrategyFactory,
) : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val battle = ctx.battle as? EntityBattleContext ?: return false

    return ctx.world.hasEffect(ctx.casterId, StatusEffectId.BERSERK.id) &&
        ctx.world.manaFractionOf(ctx.casterId) >= MIN_MANA_FRACTION &&
        attackStrategies.getAttackStrategy(battle).isAttackPossible(battle)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null

    val spent = ctx.world.spendAllCasterMana()
    battle.damageVariables.attackPhysicalMod = BASE_MULTIPLIER + spent / MANA_PER_MULTIPLIER
    val damage = attackStrategies.getAttackStrategy(battle).execute(battle)

    ctx.world.removeEffect(ctx.casterId, StatusEffectId.BERSERK.id)
    ctx.applyStatusEffect(ctx.casterId, StatusEffectId.MANA_SEALED)

    return damage
  }

  private companion object {
    const val MIN_MANA_FRACTION = 0.5f
    const val BASE_MULTIPLIER = 4f
    const val MANA_PER_MULTIPLIER = 10f
  }
}
