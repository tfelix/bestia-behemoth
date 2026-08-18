package net.bestia.zone.battle.skill

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId

interface SkillStrategy {
  /**
   * Determines if the attack is possible at the moment. If its not possible all calculation is aborted and the
   * attack is fully ignored. This should include all pre-checks like has the player enough mana, is a line of sight
   * required and does it exist. Are special items required or enough ammunition present in the inventory.
   * If the attack actually hits is part of the doAttack() method.
   */
  fun isAttackPossible(ctx: BattleContext): Boolean

  fun doAttack(ctx: BattleContext): Damage

  /**
   * Status effects to put on the target on top of whatever [doAttack] returned, applied by
   * [SkillExecutionService] once the result has landed.
   *
   * [net.bestia.zone.battle.damage.Buff] covers the skill whose *whole* effect is a status effect;
   * this covers the one that does something else and leaves a mark - First Aid heals and then blocks
   * itself on that target for a minute. Applied only when the skill actually resolved, so a fizzled
   * cast leaves no mark.
   */
  fun effectsOnTarget(ctx: BattleContext): List<StatusEffectId> = emptyList()
}
