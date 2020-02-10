package net.bestia.zoneserver.battle

abstract class AttackCheck {

  private var next: AttackCheck? = null

  fun addCheck(nextCheck: AttackCheck) {
    next = nextCheck
  }

  fun isAttackPossible(): Boolean {
    if (!checkAttackCondition()) {
      return false
    }

    return next?.let { it.isAttackPossible() } ?: true
  }

  abstract fun checkAttackCondition(): Boolean
}