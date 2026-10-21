package net.bestia.zone.cartography.poi

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import net.bestia.worldgen.vector.Aabb
import net.bestia.zone.cartography.render.MapService
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.DivergenceEntry
import net.bestia.zone.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.prop.persistence.DivergenceState
import net.bestia.zone.townsfolk.domain.OccupationCatalogue
import net.bestia.zone.world.GeneratedWorlds
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The live half of the map's services: what play takes away, and that the revision says so.
 *
 * The divergence registry is mocked only to capture the listener the index registers - the listener is the
 * whole contract between the two, and calling it is exactly what a felled building does on the tick.
 */
class MapPoiIndexTest {

  private val generated = GeneratedWorlds.of(SEED)

  private val depleted = slot<(Long, DivergenceEntry) -> Unit>()

  private val divergence = mockk<WorldObjectDivergenceRegistry> {
    every { onDepleted(capture(depleted)) } just runs
  }

  private val worldService = mockk<WorldService> {
    every { generated } returns this@MapPoiIndexTest.generated
  }

  private val index = MapPoiIndex(worldService, OccupationCatalogue().apply { load() }, divergence)

  private val world = Aabb(0.0, 0.0, generated.config.widthMetres, generated.config.heightMetres)

  @Test
  fun `a destroyed host building takes its service off the map`() {
    val shop = assertNotNull(index.liveIn(world).firstOrNull { it.business != null }, "seed $SEED has no service")
    val before = index.revision

    depleted.captured(shop.id, DivergenceEntry(StaticEntityKind.BUILDING_SHOP, DivergenceState.DEPLETED, null))

    assertTrue(index.liveIn(world).none { it.id == shop.id }, "the service outlived its building")
    assertNotEquals(before, index.revision, "a building fell and the revision did not move")
  }

  @Test
  fun `a felled tree or a building that will come back changes nothing`() {
    val shop = assertNotNull(index.liveIn(world).firstOrNull { it.business != null })
    val before = index.revision

    depleted.captured(shop.id, DivergenceEntry(StaticEntityKind.TREE, DivergenceState.DEPLETED, null))
    depleted.captured(
      shop.id, DivergenceEntry(StaticEntityKind.BUILDING_SHOP, DivergenceState.DEPLETED, Instant.now())
    )

    assertTrue(index.liveIn(world).any { it.id == shop.id })
    assertEquals(before, index.revision, "nothing a map shows changed, so no client should re-download")
  }

  @Test
  fun `buildings destroyed before this run are gone from the start`() {
    val shop = assertNotNull(index.liveIn(world).firstOrNull { it.business != null })

    index.seedDestroyed(
      mapOf(shop.id to DivergenceEntry(StaticEntityKind.BUILDING_SHOP, DivergenceState.DEPLETED, null))
    )

    assertTrue(index.liveIn(world).none { it.id == shop.id })
  }

  @Test
  fun `a ward stone is on the map until it is destroyed`() {
    val stone = 9_000_001L
    index.onWardStoneRaised(stone, 1_000.0, 2_000.0)

    val listed = index.liveIn(Aabb(0.0, 0.0, 4_000.0, 4_000.0)).single { it.id == stone }
    assertEquals(MapService.WARD_STONE, listed.kind)
    assertTrue(index.liveIn(Aabb(4_000.0, 0.0, 8_000.0, 4_000.0)).none { it.id == stone }, "listed outside its tile")

    val before = index.revision
    index.onWardStoneDestroyed(9_000_002L)
    assertEquals(before, index.revision, "an unrelated entity's destruction moved the revision")

    index.onWardStoneDestroyed(stone)
    assertTrue(index.liveIn(world).none { it.id == stone })
    assertNotEquals(before, index.revision)
  }

  private companion object {
    const val SEED = 42L
  }
}
