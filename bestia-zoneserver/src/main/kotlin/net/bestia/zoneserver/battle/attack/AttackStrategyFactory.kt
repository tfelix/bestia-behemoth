package net.bestia.zoneserver.battle.attack

import net.bestia.model.battle.AttackType
import net.bestia.zoneserver.battle.EntityBattleContext
import net.bestia.zoneserver.battle.damage.*
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom

@Component
class AttackStrategyFactory {

  private val random = ThreadLocalRandom.current()
  private val magicCalculator = MagicDamageCalculator()
  private val meleeCalculator = MeleePhysicalDamageCalculator()

  fun getAttackStrategy(ctx: EntityBattleContext): AttackStrategy {
    return when (ctx.usedAttack.attackType) {
      AttackType.MELEE_PHYSICAL -> MeleePhysicalAttackStrategy(ctx, meleeCalculator, random)
      AttackType.RANGED_PHYSICAL -> RangedPhysicalAttackStrategy(ctx, meleeCalculator, random)
      AttackType.MAGIC -> MagicAttackStrategy(ctx, magicCalculator)
      AttackType.NO_DAMAGE -> NoDamageAttackStrategy(ctx)
    }
  }
}
