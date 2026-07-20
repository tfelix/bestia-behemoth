package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.skill.BasicMagicSkillStrategy
import org.springframework.stereotype.Component

/**
 * Firebolt (`skills.yml` id 5): a channelled single-target fire bolt.
 *
 * Declared `NO_DAMAGE` in the catalog, which in this codebase means "script-driven" rather than
 * "harmless" - the script computes its own damage, exactly like [Heal] computes its own healing.
 * A `MAGIC` typing would be the natural fit but [net.bestia.zone.battle.damage.MagicDamageCalculator]
 * is still unimplemented, so the formula lives here for now.
 *
 * Inherits the line-of-sight and range gating from [BasicMagicSkillStrategy]. Because the cast is
 * channelled, that check runs when the cast *completes*, so walking out of range or breaking line of
 * sight mid-cast makes the bolt fizzle.
 */
@Component
class Firebolt(
  losService: LineOfSightService,
) : BasicMagicSkillStrategy(losService) {

  override fun doAttack(ctx: BattleContext): Damage {
    return when (ctx) {
      is EntityBattleContext -> firebolt(ctx)
      is GroundBattleContext -> Miss
    }
  }

  override fun isAttackPossible(ctx: BattleContext): Boolean {
    return if (ctx is EntityBattleContext) {
      super.isAttackPossible(ctx)
    } else {
      false
    }
  }

  private fun firebolt(ctx: EntityBattleContext): Damage {
    val attacker = ctx.attacker
    val base = (attacker.level / 4 + attacker.statusValues.intelligence) * ctx.usedAttack.level
    val matk = attacker.derivedStatusValues.matk + ctx.weapon.matk
    val mitigated = base + matk - ctx.defender.defense.magicDefense

    // A landed bolt always chips at least a point off, however tanky the target.
    return HitDamage(mitigated.coerceAtLeast(MIN_DAMAGE))
  }

  companion object {
    private const val MIN_DAMAGE = 1
  }
}
