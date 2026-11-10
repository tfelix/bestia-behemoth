package net.bestia.zone.battle.damage

sealed class Damage(amount: Int) {
  abstract val amount: Int

  /** How many blows [amount] is the sum of, shown by the client as that many numbers. */
  open val hits: Int
    get() = 1

  // Guards the constructor parameter, not the property: a data subclass assigns its `amount` backing field
  // after this runs, so reading the property here saw 0 for every value - and let a negative reach the wire,
  // where `uint32` turns it into billions.
  init {
    require(amount >= 0) { "Amount can not be negative." }
  }
}

/**
 * The same kind of number for another [amount], once a modifier weighed it. Only a blow is weighed: a heal, a
 * miss and true damage come back unchanged.
 */
fun Damage.weighedTo(amount: Int): Damage {
  return when (this) {
    is HitDamage -> copy(amount = amount)
    is CriticalHit -> copy(amount = amount)
    is Heal, is TrueDamage, Miss, Blocked -> this
  }
}

/**
 * Normal hit damage.
 */
data class HitDamage(
  override val amount: Int,
  override val hits: Int = 1
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
  override val amount: Int,
  override val hits: Int = 1
) : Damage(amount)

/**
 * True damage will (in most cases) hit the entity without modifications
 * of status effects or equipments.
 */
data class TrueDamage(
  override val amount: Int
) : Damage(amount)

/** The blow connected but a shield caught it, as Auto Guard does. Shown as "Block" rather than a miss. */
data object Blocked : Damage(0) {
  override val amount: Int
    get() = 0
}

/**
 * No damage as the attack was a miss.
 */
data object Miss : Damage(0) {
  override val amount: Int
    get() = 0
}
