package net.bestia.zone.battle.attack.scripts

import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.attack.BasicMagicAttackStrategy
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.Miss

class Heal(
  losService: LineOfSightService,
) : BasicMagicAttackStrategy(losService) {

  override fun doAttack(ctx: BattleContext): Damage {
    return when (ctx) {
      is EntityBattleContext -> heal(ctx)
      is GroundBattleContext -> return Miss
    }
  }

  override fun isAttackPossible(ctx: BattleContext): Boolean {
    return if(ctx is EntityBattleContext) {
      super.isAttackPossible(ctx)
    } else {
      false
    }
  }

  private fun heal(ctx: EntityBattleContext): Damage {
    val effectFac = ctx.damageVariables.healMod + ctx.damageVariables.healMod
    val baseAmount = ((ctx.attacker.level + ctx.attacker.statusValues.intelligence) / 5) * ctx.usedAttack.level * 3
    val matk = ctx.weapon.upgradeLevel * ctx.weapon.upgradeLevel + ctx.weapon.matk + ctx.attacker.derivedStatusValues.matk
    val healthRestored = (baseAmount * effectFac) / 100 + matk

    return Heal(healthRestored.toInt())
  }
}