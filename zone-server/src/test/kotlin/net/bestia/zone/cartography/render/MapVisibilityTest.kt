package net.bestia.zone.cartography.render

import net.bestia.worldgen.vector.FeatureKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The disclosure rule, tested as a rule rather than as a list of cases.
 *
 * `MapVisibility.of` is an exhaustive `when` with no `else`, so adding a [FeatureKind] does not compile until
 * somebody classifies it - and that is the guard the map relies on. This exists because the compiler cannot
 * check the *second* half of the argument: that whoever classifies the new kind does not reach for a
 * plausible-looking `PLACE` for something a player is meant to find by walking to it.
 *
 * So [everything a prospector would pay for stays off the map] names the kinds outright. A kind moved out of
 * `SECRET` fails here, which is the moment to argue about it rather than months later when a client is
 * quietly shipping ore positions.
 *
 * This matters more now than when only tiles existed: `MapTileService.places` sends structured coordinates
 * over HTTP, so a leak here is not a symbol somebody has to interpret off an image - it is a list of exact
 * positions.
 */
class MapVisibilityTest {

  @Test
  fun `everything a prospector would pay for stays off the map`() {
    val secret = setOf(
      FeatureKind.ORE_DEPOSIT,
      FeatureKind.CAVE_HOARD,
      FeatureKind.CAVE_SYSTEM,
      FeatureKind.CAVE_PASSAGE,
      FeatureKind.CAVE_ENTRANCE,
      FeatureKind.BESTIA_SPAWN
    )

    for (kind in secret) {
      assertEquals(MapVisibility.SECRET, MapVisibility.of(kind), "$kind must stay secret")
    }
  }

  @Test
  fun `a secret kind is undrawable at every zoom, however far in you are`() {
    // The zoom bands are a generalisation rule and this is not one of them - so it is checked at the finest
    // scale the pyramid has as well as the coarsest, because a threshold comparison that let SECRET through
    // at level 0 would look exactly like working code.
    for (metresPerPixel in listOf(0.5, 1.0, 16.0, 512.0, Double.MAX_VALUE)) {
      assertFalse(
        MapVisibility.draws(FeatureKind.ORE_DEPOSIT, metresPerPixel),
        "an ore body must not draw at $metresPerPixel m/px"
      )
      assertFalse(
        MapVisibility.draws(FeatureKind.BESTIA_SPAWN, metresPerPixel),
        "a den must not draw at $metresPerPixel m/px"
      )
    }
  }

  @Test
  fun `every feature kind is classified`() {
    // `of` is exhaustive, so this cannot fail by omission - it fails if a kind is ever classified by a lookup
    // that can return null instead, which is the shape the argument against a whitelist warns about.
    for (kind in FeatureKind.entries) {
      assertTrue(MapVisibility.of(kind) in MapVisibility.entries, "$kind has no visibility")
    }
  }

  @Test
  fun `the bands generalise as you zoom out`() {
    // A landmark is worth a mark up close and noise on a world map; a city is worth one at every zoom. If
    // these ever invert, the map either loses its towns or fills with waystones.
    assertTrue(MapVisibility.draws(FeatureKind.POI, 16.0))
    assertFalse(MapVisibility.draws(FeatureKind.POI, 512.0))

    assertTrue(MapVisibility.draws(FeatureKind.SETTLEMENT, 16.0))
    assertTrue(MapVisibility.draws(FeatureKind.SETTLEMENT, 512.0))
  }
}
