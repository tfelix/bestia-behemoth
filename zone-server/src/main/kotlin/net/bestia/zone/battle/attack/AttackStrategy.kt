package net.bestia.zone.battle.attack

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.damage.Damage

interface AttackStrategy {
  /**
   * Determines if the attack is possible at the moment. If its not possible all calculation is aborted and the
   * attack is fully ignored. This should include all pre-checks like has the player enough mana, is a line of sight
   * required and does it exist. Are special items required or enough ammunition present in the inventory.
   * If the attack actually hits is part of the doAttack() method.
   */
  fun isAttackPossible(ctx: BattleContext): Boolean

  fun doAttack(ctx: BattleContext): Damage
}