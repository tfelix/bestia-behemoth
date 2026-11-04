package net.bestia.worldgen.civ

import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles

/**
 * What kind of way a [FeatureKind.ROAD] is: a trail, a dirt road or a cobbled road.
 *
 * Chosen by `SettlementStage` from simulated traffic - village spurs are always trails - and **stored** on the
 * feature as [Profiles.CHANNEL_ROAD_CLASS]. It used to be a private enum that was thrown away once the widths
 * were set, which left the map renderer and the economy guessing the class back from `half_width` against
 * thresholds that went stale the first time the widths were retuned: every road was inked as a highway and no
 * road as a track. Read it with [of] rather than inferring it from geometry.
 *
 * ### Two widths, and they are different things
 *
 * [halfWidth] and [shoulder] are the *bed*: the ground the road flattens. The *tread* is the strip traffic
 * actually wore or paved inside it, which is what a player sees as the road, and it is narrower, wanders inside
 * the bed and frays at the edges - `voxel/RoadSurfaces` draws it.
 *
 * ### Why the bed is wider than a cart is
 *
 * A medieval road is not a carriageway with kerbs; it is a right of way. The Statute of Winchester's answer
 * to highway robbery was to clear two hundred feet either side of the king's roads, and what a traveller
 * saw was a worn strip wide enough for carts to pass, with the beaten ground spreading wherever the middle
 * had turned to mud. The first numbers here - a 3.2 m track, a 6 m road - were the *vehicle's* width rather
 * than the way's, and at a metre per voxel that draws a footpath through the trees.
 *
 * So the carriageway is 10 m, 13 m and 16 m across by class, and `SettlementStage.roadFeature` varies each of
 * them along its length rather than extruding one number end to end.
 *
 * ### And why the shoulder grew with them
 *
 * The shoulder is the verge: the band over which the profile eases from the running surface back to
 * whatever the ground was doing. Its width is what sets the batter of every cut and fill the road makes,
 * because `Profiles.road` smoothsteps across it - so the steepest slope it can produce is one and a half
 * times the height it has to make up, divided by this. Three metres of it was why a mismatch of any size at
 * all arrived as a wall. Ten to eighteen metres turns the same mismatch into a bank you can walk up, and it
 * is what a road cut into a hillside has either side of it in any case.
 *
 * Declared lightest first, and the ordinal is what the channel stores, so the order is load-bearing: appending is
 * fine, reordering renames every road in a stored world.
 */
enum class RoadClass(
  /** Half the flattened carriageway, in metres. */
  val halfWidth: Double,

  /** The verge over which the profile eases back to the natural ground, in metres. */
  val shoulder: Double,

  /**
   * How far the centreline swings either side of the routed line, in metres, over [meanderWavelength].
   *
   * Largest for a trail and nearly nothing for a cobbled road: a trail was walked into existence and went round
   * every boulder and boggy patch, while a paved road was laid out by somebody who had to pay for each extra
   * metre of stone.
   */
  val meander: Double,
  val meanderWavelength: Double,

  /** A second, shorter swing on top of [meander], so a bend is not one clean sine. */
  val wobble: Double,
  val wobbleWavelength: Double
) {
  TRAIL(halfWidth = 5.0, shoulder = 10.0, meander = 22.0, meanderWavelength = 450.0, wobble = 6.0, wobbleWavelength = 150.0),
  DIRT_ROAD(halfWidth = 6.5, shoulder = 14.0, meander = 14.0, meanderWavelength = 600.0, wobble = 4.0, wobbleWavelength = 200.0),
  COBBLED(halfWidth = 8.0, shoulder = 18.0, meander = 4.0, meanderWavelength = 900.0, wobble = 0.0, wobbleWavelength = 300.0);

  companion object {

    /** Every number above, for `SettlementStage.paramsVersion`: retuning a class is a different world. */
    fun catalogueDigest(): Long {
      val digest = ParamsDigest()
      for (road in entries) {
        digest.nested(
          "${road.ordinal}:${road.name}",
          ParamsDigest()
            .put("halfWidth", road.halfWidth)
            .put("shoulder", road.shoulder)
            .put("meander", road.meander)
            .put("meanderWavelength", road.meanderWavelength)
            .put("wobble", road.wobble)
            .put("wobbleWavelength", road.wobbleWavelength)
            .value
        )
      }
      return digest.value
    }

    /**
     * The class of a road feature, or null when it carries none.
     *
     * Null for a `STREET`, which is built by the same `LinearFeatures.road` and carries no class, and for a
     * road built by a test that did not ask for one. An ordinal out of range is null rather than a throw for
     * the reason `TownStructures` gives about block ordinals: a category read off a `Double` should degrade.
     */
    fun of(feature: PolylineFeature): RoadClass? {
      val stations = feature.stations
      if (Profiles.CHANNEL_ROAD_CLASS !in stations.channelNames) return null

      return entries.getOrNull(stations.valueAt(stations.channel(Profiles.CHANNEL_ROAD_CLASS), 0).toInt())
    }
  }
}
