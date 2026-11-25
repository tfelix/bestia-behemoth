package net.bestia.zone.prop.omen

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.prop.PropKindRegistry
import net.bestia.zone.prop.WorldObjectResidencyService
import net.bestia.zone.prop.persistence.OmenScarRepository
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaTier
import net.bestia.zone.world.mana.ManaTiers.Change
import net.bestia.zone.world.settlement.SettlementSiteIndex
import net.bestia.zone.world.stream.ChunkService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OmenRaiserTest {

  private val config = WorldConfig(seed = 7L, widthCells = 128, heightCells = 128)
  private var biome = Biome.GRASSLAND
  private var ground = 50.0
  private val worldService = mockk<WorldService>(relaxed = true) {
    every { config } returns this@OmenRaiserTest.config
    every { generated.materializer.surface.biomeAt(any(), any()) } answers { biome }
  }
  private val async = mockk<AsyncJobExecutor> { every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() } }
  private val repository = mockk<OmenScarRepository>(relaxed = true) {
    every { save(any()) } answers { firstArg() }
  }
  private val scars = OmenScars(repository, worldService, async)
  private val source = OmenScarSource(scars, PropKindRegistry().apply { load() })
  private val residency = mockk<WorldObjectResidencyService>(relaxed = true)
  private val chunks = mockk<ChunkService> { every { surfaceElevationAt(any(), any()) } answers { ground } }
  private val settlements = mockk<SettlementSiteIndex> { every { siteCovering(any(), any()) } returns null }

  private val sut = OmenRaiser(scars, source, residency, worldService, chunks, settlements)
  private val world = testWorld()

  private fun risingCells(count: Int, from: ManaTier = ManaTier.UNEASY, to: ManaTier = ManaTier.OMINOUS) =
    (0 until count).map { Change(Cell(100 + it % 40, 100 + it / 40), from, to) }

  @Test
  fun `about a fifth of the cells turning ominous get a scar`() {
    sut.onTiersChanged(world, risingCells(1_000))

    assertEquals(200.0, scars.count.toDouble(), 40.0)
    verify(exactly = scars.count) { residency.placeNow(world, any()) }
  }

  @Test
  fun `the same rise raises no second scar`() {
    sut.onTiersChanged(world, risingCells(200))
    val first = scars.count

    sut.onTiersChanged(world, risingCells(200))

    assertEquals(first, scars.count)
  }

  @Test
  fun `a rise below ominous and a fall raise nothing`() {
    sut.onTiersChanged(world, risingCells(200, ManaTier.CALM, ManaTier.UNEASY))
    sut.onTiersChanged(world, risingCells(200, ManaTier.STORMS, ManaTier.OMINOUS))

    assertEquals(0, scars.count)
  }

  @Test
  fun `water takes no scar`() {
    ground = -5.0

    sut.onTiersChanged(world, risingCells(200))

    assertEquals(0, scars.count)
  }

  @Test
  fun `every scar is served in its chunk column and looks like its ground`() {
    biome = Biome.DESERT
    sut.onTiersChanged(world, risingCells(200))

    // The cells cover x 12.8-17.9 km and y 12.8-13.5 km; a scar sits up to 48 m off its cell centre.
    val chunk = config.chunkSize
    val sites = (12_700 / chunk..18_000 / chunk).flatMap { x ->
      (12_700 / chunk..13_500 / chunk).flatMap { y -> source.sitesIn(ChunkPos(x, y, 0)) }
    }

    assertEquals(scars.count, sites.size)
    assertTrue(sites.all { it.kind == StaticEntityKind.OMEN_GLASS_SAND && it.propId == 0L })
  }

  @Test
  fun `each kind of ground takes its own scar`() {
    assertEquals(StaticEntityKind.OMEN_STONE_RING, OmenRaiser.kindFor(Biome.GRASSLAND, 1))
    assertEquals(StaticEntityKind.OMEN_FLOATING_STONES, OmenRaiser.kindFor(Biome.ALPINE, 1))
    assertEquals(StaticEntityKind.OMEN_CRYSTAL_BLOOM, OmenRaiser.kindFor(Biome.SWAMP, 1))
    assertEquals(StaticEntityKind.OMEN_TWISTED_TREE, OmenRaiser.kindFor(Biome.TEMPERATE_FOREST, 1))
    assertEquals(StaticEntityKind.OMEN_FUNGUS_RING, OmenRaiser.kindFor(Biome.TEMPERATE_FOREST, 3))
    assertNull(OmenRaiser.kindFor(Biome.OCEAN, 1))
  }
}
