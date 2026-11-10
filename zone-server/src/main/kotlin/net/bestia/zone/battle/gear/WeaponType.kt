package net.bestia.zone.battle.gear

/**
 * What kind of weapon an item is, from the design docs' weapon table. It decides which masters may hold it, see
 * [net.bestia.zone.item.profession.Profession], and which skills it allows - a Brandish Spear needs a spear.
 * A bestia is not limited by weapon type, only by the slots of its species.
 */
enum class WeaponType(
  val hands: Hands,
) {
  DAGGER(Hands.ONE),
  FIST(Hands.ONE),
  WHIP(Hands.ONE),
  SWORD(Hands.ONE),
  GREATSWORD(Hands.TWO),
  AXE(Hands.ONE),
  GREATAXE(Hands.TWO),
  MACE(Hands.ONE),
  HAMMER(Hands.TWO),
  SPEAR(Hands.ONE),
  POLEARM(Hands.TWO),
  WAND(Hands.ONE),
  STAFF(Hands.TWO),
  TOME(Hands.OFF_HAND),
  BOW(Hands.TWO),
  CROSSBOW(Hands.TWO),
  SLING(Hands.ONE),
  THROWN(Hands.ONE),
  INSTRUMENT(Hands.TWO),
  TOOL(Hands.ONE);

  enum class Hands {
    ONE,
    TWO,

    /** Held in the left hand beside a one-handed weapon, as a shield is. */
    OFF_HAND,
  }

  val twoHanded: Boolean
    get() = hands == Hands.TWO

  /** Held in the left hand rather than the right. */
  val isOffHand: Boolean
    get() = hands == Hands.OFF_HAND
}
