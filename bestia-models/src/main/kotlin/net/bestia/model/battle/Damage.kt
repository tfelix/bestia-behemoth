package net.bestia.model.battle

sealed class Damage {
  abstract val amount: Int

  init {
    require(amount >= 0) { "Amount can not be negative." }
  }
}

data class NormalHit(
    override val amount: Int
) : Damage()

data class Heal(
    override val amount: Int
) : Damage()

data class CriticalHit(
    override val amount: Int
) : Damage()

data class TrueDamage(
    override val amount: Int
) : Damage()

object Miss : Damage() {
  override val amount: Int
    get() = 0
}
