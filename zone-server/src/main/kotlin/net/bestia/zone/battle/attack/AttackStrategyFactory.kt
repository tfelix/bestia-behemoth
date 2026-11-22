package net.bestia.zone.battle.attack

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.MagicDamageCalculator
import net.bestia.zone.battle.damage.MeleePhysicalDamageCalculator
import net.bestia.zone.battle.damage.RangedPhysicalDamageCalculator
import org.springframework.stereotype.Component
import java.util.Random

/**
 * Picks how an attack resolves, from its [AttackType] and nothing else.
 *
 * Unlike [net.bestia.zone.casting.SkillStrategyFactory] there is no registry and no name lookup: the set of ways to hit somebody
 * is closed and lives in [AttackType], so a missing case here is a compile error rather than a boot warning.
 *
 * [random] is a parameter so a test can make the hit and crit rolls deterministic; production wants the
 * default, which is per-thread (see [ThreadLocalRandomSource]).
 */
@Component
class AttackStrategyFactory(
  lineOfSightService: LineOfSightService,
  random: Random = ThreadLocalRandomSource,
) {

  private val meleeStrategy =
    PhysicalAttackStrategy(MeleePhysicalDamageCalculator(random), lineOfSightService, random)

  private val rangedStrategy =
    PhysicalAttackStrategy(RangedPhysicalDamageCalculator(random), lineOfSightService, random)

  /** Every spell lands through this one, a skill script's included (see [MagicAttackStrategy.land]). */
  val magicStrategy = MagicAttackStrategy(MagicDamageCalculator(random), lineOfSightService)

  fun getAttackStrategy(ctx: BattleContext): AttackStrategy = when (ctx.usedAttack.attackType) {
    AttackType.MELEE_PHYSICAL -> meleeStrategy
    AttackType.RANGED_PHYSICAL -> rangedStrategy
    AttackType.MAGIC -> magicStrategy
  }
}
