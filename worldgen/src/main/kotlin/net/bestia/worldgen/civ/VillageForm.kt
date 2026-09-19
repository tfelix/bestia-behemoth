package net.bestia.worldgen.civ

/**
 * The shape a village takes around the way through it.
 *
 * A village has no centre to radiate from, so what distinguishes one from another is what its way does when
 * it gets there: runs straight through, meets another, or parts around a common and closes again. These are
 * the three that read differently from above, and each is a real settlement form rather than a parameter
 * setting.
 *
 * Which one a settlement gets is decided by the ways it actually has first and by its culture second - a
 * village at a crossroads is a crossroads village whatever its people would have preferred, and
 * [RoadsideVillage] downgrades a form the ground cannot support rather than forcing it.
 */
enum class VillageForm {

  /** Houses down both sides of the one way through. What most villages on a through route are. */
  LINEAR,

  /** Two ways crossing, with houses along both arms. Needs a second way that genuinely crosses the first. */
  CROSSROADS,

  /**
   * The way parts around a common and closes again, with the houses facing in across it.
   *
   * The one form with an open space at its middle, which is also the one thing a village can offer a player
   * that a row of houses cannot: somewhere to stand that is not a street.
   */
  GREEN
}
