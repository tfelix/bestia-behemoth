package net.bestia.zone.economy

import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.pop.Sector

/**
 * One transformation a settlement performs: some commodities in, one out.
 *
 * Kept by a business, or by a whole sector for the one case the generator models that way - farming is
 * what the households left over after the roster do, so there is no `farmer` business to hang grain off.
 *
 * @param building what player damage is tested against. Deliberately the building *function* rather than
 *   the individual building: working out which of five craft buildings was the mill would mean a second
 *   copy of the generator's business placement in another module
 */
data class Trade(
  val id: String,
  /** A `BusinessType.id`, or null for a [sector] trade. Exactly one of the two is set. */
  val business: String?,
  val sector: Sector?,
  val building: BuildingFunction,
  val produces: String,
  val consumes: List<Input>,
) {

  init {
    require((business == null) != (sector == null)) {
      "Trade '$id' must name either a business or a sector, not both and not neither"
    }
  }

  /** @param perUnit how much of [commodity] one unit of the trade's output takes. */
  data class Input(val commodity: String, val perUnit: Double) {
    init {
      require(perUnit > 0.0) { "An input of $commodity needs a positive per-unit, was $perUnit" }
    }
  }
}
