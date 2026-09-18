package net.bestia.zone.economy

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.pipeline.WorldParams
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.Catchment
import net.bestia.worldgen.pop.PopulationSummary
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.world.PersistedWorld
import net.bestia.zone.world.WorldGenConfig
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.fire.Scar
import net.bestia.zone.world.fire.ScorchRegistry
import net.bestia.zone.world.ground.ColumnMask
import net.bestia.zone.world.prop.DivergenceEntry
import net.bestia.zone.world.prop.DivergenceState
import net.bestia.zone.world.prop.StaticEntityKind
import net.bestia.zone.world.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import java.time.Instant
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What burning a village's fields and knocking down its workshops takes off it.
 *
 * Two of these guard traps rather than behaviour. A regrown prop stays written down until somebody
 * walks past its column, so a sweep that trusted the map would count a tree that is standing again -
 * and a sweep that *tidied up* after itself would become a third writer of a map whose contract names
 * exactly two.
 */
class SettlementCapacityTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val sites = mockk<SettlementSiteIndex>()
  private val scorch = mockk<ScorchRegistry>()
  private val divergence = mockk<WorldObjectDivergenceRegistry>()
  private val worldService = mockk<WorldService>()
  private val settings = mockk<WorldGenConfig>()

  private val destroyed = HashSet<Long>()
  private val regrowing = HashMap<Long, Instant>()
  private var scars = emptyMap<Long, Scar>()

  private val sut = SettlementDamage(sites, scorch, divergence, catalogue, worldService, settings)

  init {
    every { sites.siteOf(VILLAGE) } returns village()
    every { sites.siteOf(neq(VILLAGE)) } returns null
    every { sites.coveringWithin(any(), any(), any()) } returns listOf(VILLAGE)
    every { worldService.config } returns WORLD.toWorldConfig()
    every { worldService.record } returns WORLD
    every { settings.paramsFor(any()) } returns WorldParams()
    every { scorch.scarredKeys() } answers { scars.keys.toList() }
    every { scorch.scarOf(any()) } answers { scars[firstArg()] }
    every { divergence.of(any()) } answers {
      val propId = firstArg<Long>()
      when {
        propId in regrowing -> DivergenceEntry(StaticEntityKind.TREE, DivergenceState.DEPLETED, regrowing[propId])
        propId in destroyed -> DivergenceEntry(StaticEntityKind.TREE, DivergenceState.DEPLETED, null)
        else -> null
      }
    }
  }

  @Test
  fun `an untouched village works at its full rate`() {
    assertEquals(1.0, capacityOf("farm"))
    assertEquals(1.0, capacityOf("mill"))
    assertEquals(1.0, capacityOf("bakery"))
  }

  @Test
  fun `knocking down the mill stops the mill, and nothing else`() {
    // The business-to-building join is exact rather than coarse: a business marker sits on its host
    // building's centre and both quantise to the same lattice, so this knows it was the mill.
    destroyed.add(MILL)

    assertEquals(1.0 - SettlementDamage.WORKPLACE_CEILING, capacityOf("mill"))
    assertEquals(1.0, capacityOf("bakery"), "the bakery was taken down with the mill")
    assertEquals(1.0, capacityOf("farm"), "the fields were taken down with the mill")
  }

  @Test
  fun `one of two bakeries is half a bakery`() {
    destroyed.add(BAKERY_A)

    assertEquals(0.5, capacityOf("bakery"))
  }

  @Test
  fun `I20 - losing every workshop leaves a fifth, not nothing`() {
    // The ceiling is per channel and is now the only thing between a determined player and a town that
    // never recovers, because a destroyed building is permanent. A perfect siege leaves it wretched.
    destroyed.addAll(listOf(MILL, BAKERY_A, BAKERY_B))

    assertEquals(1.0 - SettlementDamage.WORKPLACE_CEILING, capacityOf("mill"))
    assertEquals(1.0 - SettlementDamage.WORKPLACE_CEILING, capacityOf("bakery"))
  }

  @Test
  fun `a tree that has already grown back is not damage`() {
    // The trap. Eviction only runs on materialised columns, so a regrown prop keeps its row until
    // somebody walks past - and a sweep that trusted the row would go on punishing the town forever.
    regrowing[MILL] = Instant.now().minusSeconds(60)

    assertEquals(1.0, capacityOf("mill"), "a workplace that came back is still being counted as gone")
  }

  @Test
  fun `one still growing back is`() {
    regrowing[MILL] = Instant.now().plusSeconds(600)

    assertEquals(1.0 - SettlementDamage.WORKPLACE_CEILING, capacityOf("mill"))
  }

  @Test
  fun `and the sweep never tidies the map it reads`() {
    regrowing[MILL] = Instant.now().minusSeconds(60)
    capacityOf("mill")

    verify(exactly = 0) { divergence.evictRegrown(any()) }
  }

  @Test
  fun `burning the fields thins the harvest, and only the harvest`() {
    burn(squareMetres = farmedSquareMetres() * 0.25)

    assertTrue(capacityOf("farm") < 0.8, "a quarter of the fields burnt and the farm is at ${capacityOf("farm")}")
    assertEquals(1.0, capacityOf("mill"), "burning a field stopped the mill turning, which it does not")
  }

  @Test
  fun `I20 - and a siege of fire leaves three fifths of the harvest`() {
    // Twice the fields, which is far past the ceiling however the weighting falls.
    burn(squareMetres = farmedSquareMetres() * 2.0)

    assertEquals(1.0 - SettlementDamage.SCORCH_CEILING, capacityOf("farm"))
  }

  @Test
  fun `I19 - more damage is never better`() {
    val shares = listOf(0.0, 0.05, 0.15, 0.3, 0.5)

    val outcomes = shares.map { share ->
      burn(squareMetres = farmedSquareMetres() * share)
      capacityOf("farm")
    }

    assertEquals(outcomes.sortedDescending(), outcomes, "burning more of the fields helped: $outcomes")
  }

  @Test
  fun `a fire out in the country belongs to nobody`() {
    burn(squareMetres = farmedSquareMetres() * 0.5, offsetChunks = 4_000)

    assertEquals(1.0, capacityOf("farm"), "a fire beyond the catchment was charged to the village")
  }

  @Test
  fun `a settlement that was never founded has nothing to lose`() {
    assertEquals(1.0, sut.capacityOf(VILLAGE + 1, "farm"))
  }

  /**
   * Scars enough columns to cover [squareMetres], spreading outward from [offsetChunks].
   *
   * Outward from the centre rather than along a line, which is what a fire does and, less obviously,
   * what the test needs: a strip of columns walks out of a seven-kilometre catchment after two
   * hundred of them, so a line-shaped burn silently stops counting however much more is asked for.
   */
  private fun burn(squareMetres: Double, offsetChunks: Int = 0) {
    val cellsPerColumn = CHUNK * CHUNK
    val wanted = Math.ceil(squareMetres / cellsPerColumn).toInt().coerceAtLeast(1)
    val span = Math.ceil(Math.sqrt(wanted.toDouble())).toInt() + 1

    val burning = HashMap<Long, Scar>()
    // Nearest first, so a small burn lands on the ground the village claims most strongly.
    val columns = (-span..span).flatMap { x -> (-span..span).map { y -> x to y } }
      .sortedBy { (x, y) -> x * x + y * y }
      .take(wanted)

    for ((x, y) in columns) {
      val key = ((x + offsetChunks).toLong() shl 32) or (y.toLong() and 0xFFFF_FFFFL)
      burning[key] = Scar(ColumnMask(CHUNK).apply { repeat(cellsPerColumn) { set(it) } }, burnedAtSecond = 0)
    }

    scars = burning
    sut.invalidate()
  }

  /**
   * The area the village actually works, reproduced here so the tests above can talk in fractions of
   * it rather than in magic numbers of square metres.
   */
  private fun farmedSquareMetres(): Double {
    val radius = Catchment.radiusOf(SettlementTier.VILLAGE, WorldParams().economy)
    val worked = POPULATION / FOOD_CAPACITY

    return PI * radius * radius * Catchment.MEAN_CLAIM_WEIGHT * worked * CEREAL_SHARE
  }

  private fun capacityOf(trade: String): Double {
    sut.invalidate()
    return sut.capacityOf(VILLAGE, trade)
  }

  private fun village() = SettlementSite(
    index = VILLAGE,
    centre = Vec2d(0.0, 0.0),
    tier = SettlementTier.VILLAGE,
    population = PopulationSummary(
      settlement = VILLAGE,
      position = Vec2d(0.0, 0.0),
      population = POPULATION.toInt(),
      wealth = 0.4,
      householdCount = 75,
      seed = 1L,
      businesses = emptyList(),
      sectors = IntArray(7),
      traffic = 1.5,
      foodCapacity = FOOD_CAPACITY,
      cerealShare = CEREAL_SHARE,
    ),
    unordered = listOf(
      building(MILL, "miller", BuildingFunction.CRAFT),
      building(BAKERY_A, "baker", BuildingFunction.CRAFT),
      building(BAKERY_B, "baker", BuildingFunction.CRAFT),
      building(BARN, null, BuildingFunction.FARM),
    ),
  )

  private fun building(propId: Long, trade: String?, function: BuildingFunction) = SettlementSite.Building(
    propId = propId,
    function = function,
    centre = Vec2d(propId.toDouble(), 0.0),
    door = Vec2d(propId.toDouble(), 0.0),
    floorElevation = 0.0,
    businessType = trade?.let { id -> BusinessCatalogue.ALL.indexOfFirst { it.id == id } }
      ?: SettlementSiteIndex.NO_BUSINESS,
  )

  private companion object {
    const val VILLAGE = 12
    const val MILL = 100L
    const val BAKERY_A = 200L
    const val BAKERY_B = 300L
    const val BARN = 400L
    const val CHUNK = 32

    const val POPULATION = 300.0

    /** Comfortably above the population, as a real catchment is - the village works a part of it. */
    const val FOOD_CAPACITY = 3_000.0
    const val CEREAL_SHARE = 0.4

    val WORLD = PersistedWorld(
      name = "test",
      seed = 1L,
      widthCells = 64,
      heightCells = 64,
      cellSizeMetres = 1_000.0,
      chunkSize = CHUNK,
      chunkHeight = 32,
      voxelSizeMetres = 1.0,
      seaLevelMetres = 0.0,
      wrapX = false,
      wrapY = false,
      pipelineVersion = 1L,
      blockPaletteVersion = 1L,
      chunkFormatVersion = 1,
      shapeVersion = 1L,
      createdAt = Instant.EPOCH,
    )
  }
}
