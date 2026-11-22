package net.bestia.zone.battle

/**
 * A body's size, which weighs a weapon's attack against it (see [SizeModifier]) and sets a species' default weight.
 *
 * @property bodyWeightKg what a species of this size weighs when its row names no weight, from the bestia design
 *   docs: 15 kg small, 60 kg medium, 250 kg big
 */
enum class Size(private val bodyWeightKg: Int) {

  /** About as big as a medium sized dog, or smaller. */
  SMALL(15),

  /** About the size of a human. */
  MEDIUM(60),

  /** Far bigger than a human: a bear, an elephant, a wyrm. */
  BIG(250);

  /** The documented default body weight; a boss weighs twice its kin. */
  fun defaultBodyWeightKg(boss: Boolean): Int {
    return if (boss) bodyWeightKg * 2 else bodyWeightKg
  }
}
