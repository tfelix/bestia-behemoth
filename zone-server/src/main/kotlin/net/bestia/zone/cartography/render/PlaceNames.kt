package net.bestia.zone.cartography.render

import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.poi.PoiChannels
import net.bestia.worldgen.poi.PoiKind
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker

/**
 * The one place a marker becomes something a reader can name.
 *
 * ### Why this is not inside [PlaceInk]
 *
 * It was, and it was private there, which was fine for exactly as long as the drawn tile was the only thing
 * that wanted a name. It is not any more: `MapTileService.features` answers the same question over HTTP so the
 * client can label the symbols a served tile carries without labels of its own. Two copies of "which channel
 * holds the name seed" would drift, and the drift would be silent - a town labelled one thing on the atlas and
 * another in the client is not an error anybody's build catches.
 *
 * ### The two-sided join, and the one that is not
 *
 * A **settlement** keeps its name seed on its chronicle record and its culture on the marker, so naming one
 * needs both - the same join `civ/SettlementSpawnPoints` performs, because placement knows the culture and
 * history knows the name.
 *
 * A **built site** - a ruin, a tomb, a fort - carries `SiteChannels.NAME_SEED` and `CULTURE` on the marker
 * itself, so it needs no chronicle lookup at all. `PlaceInk` never asked for these, which is the only reason
 * every ruin in the world has been drawn as an unlabelled broken square: the seed was always there.
 *
 * A seed is 48 bits and a station channel is a `Double`, which holds 53 bits of integer exactly, so the value
 * round-trips. `SettlementChannels.INDEX` exists rather than a `FeatureId` for the same arithmetic reason,
 * and says so.
 *
 * ### A POI has a kind, not a name
 *
 * There is no seed to roll: a waystone is called "waystone" in every world. [poiKindOf] therefore returns the
 * enum, and what a client prints for it is the client's business - the six labels are a closed set, so unlike
 * a generated name they can go through Godot's build-time `tr()` tables. Sending the kind rather than
 * `PoiKind.label` is what keeps that possible.
 */
object PlaceNames {

  /** [SettlementTier] of a `SETTLEMENT` marker, or null for anything else. */
  fun tierOf(marker: PointMarker): SettlementTier? {
    val ordinal = marker.optionalAttribute(SettlementChannels.TIER)?.toInt() ?: return null
    return SettlementTier.entries.getOrNull(ordinal)
  }

  /** [PoiKind] of a `POI` marker, or null for anything else. */
  fun poiKindOf(marker: PointMarker): PoiKind? {
    val ordinal = marker.optionalAttribute(PoiChannels.KIND)?.toInt() ?: return null
    return PoiKind.entries.getOrNull(ordinal)
  }

  /**
   * The proper name of a place, or null when it has none.
   *
   * Null is a real answer and not a failure: a world generated without a history has no name seeds at all, and
   * a landmark nobody built has no name to have. Callers draw the symbol either way.
   */
  fun nameOf(chronicle: Chronicle, marker: PointMarker): String? = when (marker.kind) {
    FeatureKind.SETTLEMENT -> settlementName(chronicle, marker)
    else -> siteName(marker)
  }

  private fun settlementName(chronicle: Chronicle, marker: PointMarker): String? {
    val index = marker.optionalAttribute(SettlementChannels.INDEX)?.toInt() ?: return null
    val culture = marker.optionalAttribute(SettlementChannels.CULTURE)?.toInt() ?: return null
    val record = chronicle.settlements.getOrNull(index) ?: return null
    if (record.nameSeed == 0L) return null

    return Names.place(record.nameSeed, culture)
  }

  private fun siteName(marker: PointMarker): String? {
    val seed = marker.optionalAttribute(SiteChannels.NAME_SEED)?.toLong() ?: return null
    if (seed == 0L) return null
    val culture = marker.optionalAttribute(SiteChannels.CULTURE)?.toInt() ?: return null

    return Names.place(seed, culture)
  }

  /**
   * Coarsest zoom each tier survives to, in metres per pixel.
   *
   * A world map that marked every hamlet would be a map of dots. Cities and towns carry the shape of a
   * country, so they stay at every zoom; the smaller two appear as you come in.
   */
  val SettlementTier.visibleTo: Double
    get() = when (this) {
      SettlementTier.CITY -> Double.MAX_VALUE
      SettlementTier.TOWN -> Double.MAX_VALUE
      SettlementTier.VILLAGE -> 96.0
      SettlementTier.HAMLET -> 40.0
    }
}
