package net.bestia.zone.battle.skill

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.MeleePhysicalDamageCalculator
import org.springframework.stereotype.Component
import java.util.Random

/**
 * Picks how a basic attack resolves, from the weapon's [AttackType] and nothing else.
 *
 * Unlike [SkillStrategyFactory] there is no registry and no name lookup: the set of ways to hit somebody with
 * a weapon is closed and lives in [AttackType], so a missing case here is a compile error rather than a boot
 * warning.
 *
 * [random] is a parameter so a test can make the hit and crit rolls deterministic; production wants the
 * default, which is per-thread (see [ThreadLocalRandomSource]).
 */
@Component
class AttackStrategyFactory(
  lineOfSightService: LineOfSightService,
  random: Random = ThreadLocalRandomSource,
) {

  private val meleeCalculator = MeleePhysicalDamageCalculator(random)
  private val meleeStrategy = MeleePhysicalAttackStrategy(meleeCalculator, lineOfSightService, random)
  private val rangedStrategy = RangedPhysicalAttackStrategy(meleeCalculator, lineOfSightService, random)

  fun getAttackStrategy(ctx: BattleContext): AttackStrategy = when (ctx.usedAttack.attackType) {
    AttackType.MELEE_PHYSICAL -> meleeStrategy
    AttackType.RANGED_PHYSICAL -> rangedStrategy
    // No weapon is magic yet, and MagicDamageCalculator is unimplemented. Deliberately not TODO(): that
    // would be a NotImplementedError thrown on the tick thread, killing the system that swung.
    AttackType.MAGIC -> meleeStrategy
  }
}
