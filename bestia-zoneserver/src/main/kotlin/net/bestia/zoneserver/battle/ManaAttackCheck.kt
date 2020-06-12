package net.bestia.zoneserver.battle

import mu.KotlinLogging

private val LOG = KotlinLogging.logger { }

class ManaAttackCheck(
    private val battleCtx: EntityBattleContext
) : AttackCheck() {
  /**
   * Check if the entity has the mana needed for the attack.
   *
   * @return TRUE if the entity has enough mana to perform the attack. FALSE
   * otherwise.
   */
  override fun checkAttackCondition(): Boolean {
    val neededMana = getNeededMana(battleCtx)
    return battleCtx.attackerCondition.currentMana >= neededMana
  }

  /**
   * Calculates the needed mana for an attack. Mana cost can be reduced by
   * effects or scripts.
   *
   * @param battleCtx The [EntityBattleContext].
   * @return The actual mana costs for this attack.
   */
  private fun getNeededMana(battleCtx: EntityBattleContext): Int {
    val attack = battleCtx.usedAttack
    val neededManaMod = battleCtx.damageVariables.neededManaMod
    val neededMana = Math.ceil((attack.manaCost * neededManaMod).toDouble()).toInt()
    LOG.trace("Needed mana: {}/{}", neededMana, attack.manaCost)

    return neededMana
  }
}