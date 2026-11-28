package net.bestia.zone.master.bestia

/**
 * What a bestia is doing, for its status line. [kind] is CRAFTING, MINING, GATHERING, HAULING, FETCHING_FOOD,
 * EATING, HEALING, FIGHTING, RETREATING, RESTING_AT_HOME, SLEEPING, GUARDING or IDLE. At a station, [item] is what
 * it makes, [done] of [total], at [station] (its kind); [pause] says why the station waits, if it does.
 */
data class BestiaActivity(
  val kind: String,
  val item: String? = null,
  val done: Int? = null,
  val total: Int? = null,
  val station: String? = null,
  val pause: String? = null,
)
