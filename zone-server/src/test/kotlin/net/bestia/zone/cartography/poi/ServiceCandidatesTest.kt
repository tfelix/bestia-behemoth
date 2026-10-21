package net.bestia.zone.cartography.poi

import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.BusinessChannels
import net.bestia.worldgen.render.optionalAttribute
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.BuildingProps
import net.bestia.zone.cartography.render.MapService
import net.bestia.zone.townsfolk.domain.OccupationCatalogue
import net.bestia.zone.world.GeneratedWorlds
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the generator offers the map, against real worlds rather than fixtures.
 *
 * Swept over all three shared seeds, because a gate that drops everything passes a test that only asks "is
 * everything listed allowed". So each test also insists on having found something to check.
 */
class ServiceCandidatesTest {

  private val occupations = OccupationCatalogue().apply { load() }

  @Test
  fun `a real world offers merchants, inns and temples`() {
    val kinds = HashSet<MapService>()

    for (seed in GeneratedWorlds.SEEDS) {
      kinds += candidatesOf(seed).within(wholeWorld(seed)).map { it.kind }
    }

    for (kind in listOf(MapService.MERCHANT, MapService.INN, MapService.TEMPLE)) {
      assertTrue(kind in kinds, "no world offered a single $kind; found $kinds")
    }
  }

  @Test
  fun `only staffed trades are listed, with their keeper's hours`() {
    val keepers = occupations.all().filter { it.businessType != null }.associateBy { it.businessType }
    var checked = 0

    for (seed in GeneratedWorlds.SEEDS) {
      for (poi in candidatesOf(seed).within(wholeWorld(seed))) {
        val business = poi.business ?: continue
        val keeper = keepers[business]

        assertTrue(keeper != null, "$business is listed but nobody keeps it")
        assertEquals(keeper.shift?.fromHour, poi.openFrom, "$business opens when its keeper starts")
        assertEquals(keeper.shift?.toHour, poi.openTo, "$business closes when its keeper stops")
        checked++
      }
    }

    assertTrue(checked > 0, "no business was listed, so nothing was checked")
  }

  @Test
  fun `with nobody keeping a shop, only the markets remain`() {
    for (seed in GeneratedWorlds.SEEDS) {
      val listed = ServiceCandidates(GeneratedWorlds.of(seed).world, emptyMap()).within(wholeWorld(seed))

      assertTrue(listed.all { it.kind == MapService.MARKET }, "seed $seed listed ${listed.map { it.kind }.toSet()}")
    }
  }

  /**
   * A guard for a generator change rather than a check of today's worlds: on the shared seeds the economy
   * stage gives no ruined settlement a business at all, so this finds nothing to refuse. It is here so that the
   * day a ruin keeps its old trades in the feature store - a burnt-out smithy is good scenery - the map does
   * not start offering a travel button to it.
   */
  @Test
  fun `no business of a ruined or unfounded settlement is listed`() {
    for (seed in GeneratedWorlds.SEEDS) {
      val world = GeneratedWorlds.of(seed).world
      val listed = candidatesOf(seed).within(wholeWorld(seed)).map { it.id }.toSet()

      for (feature in world.features.all()) {
        if (feature !is PointMarker || feature.kind != FeatureKind.BUSINESS) continue
        val settlement = feature.optionalAttribute(BusinessChannels.SETTLEMENT)?.toInt() ?: continue
        val record = world.chronicle.settlements.getOrNull(settlement)
        if (record != null && record.wasFounded && !record.isRuin) continue

        val id = BuildingProps.propIdAt(feature.position.x, feature.position.y)
        assertTrue(id !in listed, "a business of fallen settlement $settlement is on the map")
      }
    }
  }

  @Test
  fun `a point on a shared tile edge belongs to exactly one tile`() {
    for (seed in GeneratedWorlds.SEEDS) {
      val candidates = candidatesOf(seed)
      val whole = candidates.within(wholeWorld(seed))
      // Halves meeting at a line through an actual service, which is the case a closed interval double-counts.
      val split = whole.firstOrNull { it.business != null } ?: continue
      val world = wholeWorld(seed)

      val west = candidates.within(Aabb(world.minX, world.minY, split.x, world.maxY))
      val east = candidates.within(Aabb(split.x, world.minY, world.maxX, world.maxY))

      assertEquals(whole.size, west.size + east.size, "seed $seed: halves disagree with the whole")
    }
  }

  @Test
  fun `the trades listed are the ones decided for the map`() {
    for (seed in GeneratedWorlds.SEEDS) {
      for (poi in candidatesOf(seed).within(wholeWorld(seed))) {
        val business = poi.business ?: continue

        assertTrue(BusinessCatalogue.ALL.any { it.id == business })
        assertEquals(MapService.forBusiness(business), poi.kind)
      }
    }
  }

  private fun candidatesOf(seed: Long) = ServiceCandidates(GeneratedWorlds.of(seed).world, occupations.hoursByTrade())

  private fun wholeWorld(seed: Long): Aabb {
    val config = GeneratedWorlds.of(seed).config
    return Aabb(0.0, 0.0, config.widthMetres, config.heightMetres)
  }
}
