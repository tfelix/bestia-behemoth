package net.bestia.zone.battle.skill

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.damage.Damage

/**
 * How a **basic attack** resolves: a sword swing, an arrow, a mob's bite. Picked by
 * [AttackStrategyFactory] from the [BattleAttack]'s [AttackType] and run by [AttackExecutionService].
 *
 * Deliberately narrower than [SkillStrategy]: an attack is a pure function of the fight snapshot, with
 * no world to write to and no script behind it. That is the whole distinction - a sword does not need
 * a catalogue entry and a scripting hook to hit somebody, so it does not get one.
 */
interface AttackStrategy {

  /** Range and line of sight. Nothing is spent when this is false. */
  fun isAttackPossible(ctx: BattleContext): Boolean

  /** The damage this swing did, including [net.bestia.zone.battle.damage.Miss] when it did not land. */
  fun execute(ctx: BattleContext): Damage
}
