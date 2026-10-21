package net.bestia.zone.cartography.render

import net.bestia.worldgen.pop.BusinessCatalogue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * That every trade the generator builds has had its map decision made.
 *
 * The check [MapVisibility] gets from an exhaustive `when`, which a map keyed by strings cannot have. A trade
 * added to `BusinessCatalogue` without an entry here would silently stay off every map - which is the safe
 * direction, and exactly why nobody would ever notice it.
 */
class MapServiceTest {

  @Test
  fun `every trade has a map decision`() {
    val trades = BusinessCatalogue.ALL.map { it.id }.toSet()

    assertEquals(
      emptySet(),
      trades - MapService.BY_BUSINESS.keys,
      "Decide in MapService.BY_BUSINESS whether these trades reach a map"
    )
  }

  @Test
  fun `no decision names a trade that does not exist`() {
    val trades = BusinessCatalogue.ALL.map { it.id }.toSet()

    assertEquals(
      emptySet(),
      MapService.BY_BUSINESS.keys - trades,
      "MapService.BY_BUSINESS names trades the catalogue no longer has"
    )
  }

  @Test
  fun `market traders are not marked twice`() {
    // Their stalls stand in market buildings, which are listed as MARKET in their own right.
    assertNull(MapService.forBusiness("market_trader"))
  }
}
