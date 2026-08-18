package net.bestia.zone.battle.damage

sealed class Damage {
  abstract val amount: Int

  init {
    require(amount >= 0) { "Amount can not be negative." }
  }
}

/**
 * Normal hit damage.
 */
data class HitDamage(
  override val amount: Int
) : Damage()

/**
 * Damage is heal.
 */
data class Heal(
  override val amount: Int
) : Damage()

/**
 * This was a critical damage and will be displayed differently.
 */
data class CriticalHit(
  override val amount: Int
) : Damage()

/**
 * True damage will (in most cases) hit the entity without modifications
 * of status effects or equipments.
 */
data class TrueDamage(
  override val amount: Int
) : Damage()

/**
 * No damage as the attack was a miss.
 */
data object Miss : Damage() {
  override val amount: Int
    get() = 0
}
