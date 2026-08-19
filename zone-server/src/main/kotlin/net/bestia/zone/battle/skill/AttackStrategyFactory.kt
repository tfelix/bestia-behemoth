package net.bestia.zone.battle.skill

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.MeleePhysicalDamageCalculator
import net.bestia.zone.battle.damage.RangedPhysicalDamageCalculator
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

  private val meleeStrategy =
    PhysicalAttackStrategy(MeleePhysicalDamageCalculator(random), lineOfSightService, random)

  private val rangedStrategy =
    PhysicalAttackStrategy(RangedPhysicalDamageCalculator(random), lineOfSightService, random)

  fun getAttackStrategy(ctx: BattleContext): AttackStrategy = when (ctx.usedAttack.attackType) {
    AttackType.MELEE_PHYSICAL -> meleeStrategy
    AttackType.RANGED_PHYSICAL -> rangedStrategy

    // No weapon is magic, so nothing reaches this. `MagicDamageCalculator` exists and would supply the
    // numbers, but a magic attack also neither misses nor crits, and that is a strategy rather than a
    // calculator - so it wants its own [AttackStrategy] rather than this one wired to a different formula.
    // Falls through to melee meanwhile: a TODO() here would be a NotImplementedError on the tick thread,
    // killing the system that swung.
    AttackType.MAGIC -> meleeStrategy
  }
}
