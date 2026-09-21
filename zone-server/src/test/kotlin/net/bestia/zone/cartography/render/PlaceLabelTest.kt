package net.bestia.zone.cartography.render

import net.bestia.worldgen.poi.PoiKind
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.world.GeneratedWorlds
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * That everything the map calls a place has something to call it.
 *
 * The client labels a place with its generated name, else `POI_<kind>`, else `FEATURE_<kind>` through Godot's
 * build-time tables, else nothing at all. A kind that reaches that last arm is drawn with a symbol and no
 * label, which on the map is indistinguishable from a kind nobody has got to yet.
 *
 * Neither half of this is checkable from the other side. The CSV is compiled into a separately released
 * artefact, so nothing compares the two at build time - the argument `ClientWorldContractTest` makes about the
 * chunk geometry. And a rare kind produces nothing on most seeds, so a sweep on its own passes by having found
 * nothing to check. So the two tests here cover each other: the closed set catches a kind classified as a place
 * with no word for it, and the sweep catches a kind that turns out to reach the client after all.
 */
class PlaceLabelTest {

  @Test
  fun `every kind the map calls a place has a word for it`() {
    val keys = ClientStrings.keys()

    for (kind in FeatureKind.entries) {
      if (!MapVisibility.of(kind).isPlace) continue
      if (kind in NAMED_BY_THEMSELVES || kind in FILLED_NOT_MARKED) continue

      assertTrue(
        "FEATURE_${kind.name}" in keys,
        "$kind is drawn as a place, and a marker of it with no name seed would have nothing to be called. " +
            "Add FEATURE_${kind.name} to ${ClientStrings.GENERAL_CSV}, or classify it out of PLACE/LANDMARK."
      )
    }
  }

  @Test
  fun `every landmark kind has a word for it`() {
    val keys = ClientStrings.keys()

    // A closed, hand-authored list, which is the whole reason `PlaceNames.poiKindOf` sends the enum rather
    // than a rendered English label.
    for (poi in PoiKind.entries) {
      assertTrue("POI_${poi.name}" in keys, "Add POI_${poi.name} to ${ClientStrings.GENERAL_CSV}")
    }
  }

  @Test
  fun `no place a real world produces goes out unlabelled`() {
    val keys = ClientStrings.keys()
    val unlabelled = LinkedHashMap<FeatureKind, Int>()
    val seen = LinkedHashSet<FeatureKind>()

    for (seed in GeneratedWorlds.SEEDS) {
      val inputs = TileInputs.of(GeneratedWorlds.of(seed))

      for (feature in inputs.featuresIn(WHOLE_WORLD)) {
        // The gates `MapTileService.placesIn` applies, at a zoom where both place bands draw.
        if (feature !is PointMarker) continue
        if (!MapVisibility.draws(feature.kind, METRES_PER_PIXEL)) continue
        if (!MapVisibility.of(feature.kind).isPlace) continue
        if (feature.kind == FeatureKind.SETTLEMENT) {
          if (PlaceNames.tierOf(feature) == null) continue
          if (!PlaceNames.wasFounded(inputs.chronicle, feature)) continue
        }

        seen += feature.kind
        val labelled = PlaceNames.nameOf(inputs.chronicle, feature) != null ||
            PlaceNames.poiKindOf(feature) != null ||
            "FEATURE_${feature.kind.name}" in keys

        if (!labelled) unlabelled[feature.kind] = (unlabelled[feature.kind] ?: 0) + 1
      }
    }

    // A sweep that found nothing to check is not a passing sweep. The floor is a fraction of what three
    // worlds actually produce, so it fails when a subsystem stops producing rather than when one gets rarer.
    assertTrue(seen.size >= MIN_KINDS_SWEPT, "Only $seen across ${GeneratedWorlds.SEEDS} - this checked nothing")
    assertTrue(unlabelled.isEmpty(), "Places the client would draw with no label at all: $unlabelled")
  }

  private companion object {

    /** Named from the chronicle and from its own kind respectively - see [PlaceNames]. */
    val NAMED_BY_THEMSELVES = setOf(FeatureKind.SETTLEMENT, FeatureKind.POI)

    /**
     * Ground the map fills rather than a thing it marks.
     *
     * Both are `AreaFeature`s, so neither reaches [PlaceInk] or `MapTileService.places` - both of which take
     * point markers only - and a word for them would be a line nothing can print. If either ever becomes a
     * marker, the sweep is what says so.
     */
    val FILLED_NOT_MARKED = setOf(FeatureKind.FIELD, FeatureKind.LAVA_POOL)

    /** Fine enough that both place bands draw: LANDMARK stops at 20 m/px and PLACE has no ceiling. */
    const val METRES_PER_PIXEL = 16.0

    /**
     * Three 256-cell worlds produced fourteen distinct place kinds when this was written - every one that
     * can be a marker except `SHRINE`. The floor sits below that, so a kind merely getting rarer does not
     * fail the run while a subsystem producing nothing at all still does.
     */
    const val MIN_KINDS_SWEPT = 11

    val WHOLE_WORLD = Aabb(-1e9, -1e9, 1e9, 1e9)
  }
}
