package net.bestia.zone.world.mana

/**
 * How the mana in a place feels, from calm to burning. Each step adds to the ones below it: an Ominous place is
 * also Uneasy. Players and the client only ever learn the tier, never the density behind it.
 */
enum class ManaTier(val from: Double, val denLevelBonus: Int = 0) {
  CALM(0.0),
  STIRRING(0.35),
  UNEASY(0.45),
  OMINOUS(0.55, denLevelBonus = 3),
  STORMS(0.60, denLevelBonus = 3),
  EVENT(0.70, denLevelBonus = 5),
  SICKNESS(0.80, denLevelBonus = 7),
  BURN(0.90, denLevelBonus = 10);

  /** Dens here breed mana-touched bestia, a few levels higher than their kin. */
  val breedsManaTouched: Boolean get() = denLevelBonus > 0

  companion object {
    /** The tier a density falls in, without memory of where it came from. */
    fun of(density: Double): ManaTier = entries.last { density >= it.from }
  }
}
