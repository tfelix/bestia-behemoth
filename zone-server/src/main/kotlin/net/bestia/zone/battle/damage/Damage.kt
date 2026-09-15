package net.bestia.zone.battle.damage

sealed class Damage(amount: Int) {
  abstract val amount: Int

  // Guards the constructor parameter, not the property: a data subclass assigns its `amount` backing field
  // after this runs, so reading the property here saw 0 for every value - and let a negative reach the wire,
  // where `uint32` turns it into billions.
  init {
    require(amount >= 0) { "Amount can not be negative." }
  }
}

/**
 * Normal hit damage.
 */
data class HitDamage(
  override val amount: Int
) : Damage(amount)

/**
 * Damage is heal.
 */
data class Heal(
  override val amount: Int
) : Damage(amount)

/**
 * This was a critical damage and will be displayed differently.
 */
data class CriticalHit(
  override val amount: Int
) : Damage(amount)

/**
 * True damage will (in most cases) hit the entity without modifications
 * of status effects or equipments.
 */
data class TrueDamage(
  override val amount: Int
) : Damage(amount)

/**
 * No damage as the attack was a miss.
 */
data object Miss : Damage(0) {
  override val amount: Int
    get() = 0
}
