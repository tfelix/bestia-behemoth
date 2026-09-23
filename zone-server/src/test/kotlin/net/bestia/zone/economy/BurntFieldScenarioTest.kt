package net.bestia.zone.economy

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.pipeline.WorldParams
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.pop.Catchment
import net.bestia.worldgen.pop.PopulationSummary
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.world.PersistedWorld
import net.bestia.zone.world.WorldGenConfig
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.fire.Scar
import net.bestia.zone.world.fire.ScorchRegistry
import net.bestia.zone.world.ground.ColumnMask
import net.bestia.zone.world.prop.WorldObjectDivergenceRegistry
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import java.time.Instant
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The sentence the whole release is measured against:
 *
 * > A player walks into a village, buys bread, burns the field outside it, comes back a Bestia day
 * > later, and the bread is more expensive.
 *
 * Everything between the fire and the price is real here - the scorch sweep, the catchment weighting,
 * the Leontief chain through grain and flour, the ledger's own step and the shop's quote. Only the
 * clock and the world are stood in for.
 *
 * Note the *scale* the last test pins. One campfire cannot move a market: a village works square
 * kilometres of ground, so what "burn the field" means is burning a real part of it. That is the
 * honest answer and it is worth a test of its own, because the alternative - tuning the sweep until a
 * single fire is dramatic - would make a farm's whole output hang on a patch the size of a room.
 */
class BurntFieldScenarioTest {

  private val catalogue = EconomyCatalogue().apply { load() }
  private val sites = mockk<SettlementSiteIndex>()
  private val scorch = mockk<ScorchRegistry>()
  private val divergence = mockk<WorldObjectDivergenceRegistry>()
  private val repository = mockk<SettlementLedgerRepository>(relaxed = true)
  private val worldService = mockk<WorldService>()
  private val settings = mockk<WorldGenConfig>()
  private val clock = mockk<BestiaClock>()

  private var now = START
  private var scars = emptyMap<Long, Scar>()

  private val asyncJobExecutor = mockk<AsyncJobExecutor>().also {
    every { it.submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }

  private var damage = newDamage()
  private var service = newService()

  private fun newDamage() = SettlementDamage(sites, scorch, divergence, catalogue, worldService, settings)

  private fun newService() = SettlementEconomyService(
    catalogue = catalogue,
    step = EconomyStep(catalogue, damage, UnclaimedProduction()),
    damage = damage,
    sites = sites,
    money = PerResidentTreasury(),
    reserve = UnlimitedReserve(),
    repository = repository,
    asyncJobExecutor = asyncJobExecutor,
    worldService = worldService,
    clock = clock,
  )

  /** A second village, same seed, same calendar - the control the burnt one is measured against. */
  private fun startOver() {
    now = START
    scars = emptyMap()
    damage = newDamage()
    service = newService()
  }

  init {
    every { sites.siteOf(VILLAGE) } returns village()
    every { sites.siteCovering(any(), any()) } returns village()
    every { sites.coveringWithin(any(), any(), any()) } returns listOf(VILLAGE)
    every { worldService.config } returns WORLD.toWorldConfig()
    every { worldService.record } returns WORLD
    every { settings.paramsFor(any()) } returns WorldParams()
    every { clock.now() } answers { now }
    every { scorch.scarredKeys() } answers { scars.keys.toList() }
    every { scorch.scarOf(any()) } answers { scars[firstArg()] }
    every { divergence.of(any()) } returns null
    every { repository.findAll() } returns emptyList()
    every { repository.save(any()) } answers { firstArg() }
  }

  @Test
  fun `burn the field, come back a day later, and the bread costs more`() {
    val before = breadPrice()

    burn(share = 0.5)
    advanceDays(1)

    val after = breadPrice()
    assertTrue(after > before, "the fields burnt and a loaf still costs $before")
  }

  @Test
  fun `and it goes on getting worse while the fire is still out there`() {
    burn(share = 0.5)

    advanceDays(1)
    val firstDay = breadPrice()
    advanceDays(4)
    val fifthDay = breadPrice()

    assertTrue(fifthDay > firstDay, "the shortage stopped biting after a day, at $firstDay")
  }

  @Test
  fun `a player would notice, once the village has spent its savings`() {
    // What a shop actually quotes, in whole coins, because that is the only number anybody sees.
    //
    // Against the *same village on the same day without the fire*, not against what it charged before
    // it. Grain swings by nearly half over a year, which is far more than any one fire does, so a
    // before-and-after reading here would mostly be measuring the harvest coming in.
    //
    // Weeks rather than the day the test above measures, and the fields burnt out rather than halved: a
    // village covers the shortfall out of its treasury while the money lasts, so what a player sees is a
    // town coping and then, a month or so later, not coping.
    advanceDays(DAYS_TO_NOTICE)
    val untouched = breadQuote(BASKET)

    startOver()
    burn(share = 1.0)
    advanceDays(DAYS_TO_NOTICE)

    assertTrue(
      breadQuote(BASKET) > untouched,
      "$BASKET loaves still quote at $untouched coins with the fields burnt out"
    )
  }

  @Test
  fun `once the rain has taken the scars the price comes back down`() {
    burn(share = 0.5)
    advanceDays(30)
    val hungry = breadPrice()

    // What regrowth does: the scar's visible mask erodes to nothing and the registry drops the column.
    // Not a timer anywhere in the economy - it heals on rain, which is somebody else's business.
    scars = emptyMap()
    damage.invalidate()
    advanceDays(60)

    assertTrue(breadPrice() < hungry, "the fields healed and bread is still at $hungry")
  }

  @Test
  fun `the shortage reaches the loaf through the grain, and shows in the grain first`() {
    // The chain, from the outside. Nothing in the sweep knows bread is made of flour: it takes the
    // farm's capacity away, and the Leontief minimum in the step carries it two stages downstream.
    burn(share = 0.5)
    advanceDays(20)

    val market = assertNotNull(service.marketOf(VILLAGE))
    val reference = assertNotNull(service.referenceOf(VILLAGE))

    for (commodity in listOf("grain", "flour", "bread")) {
      assertTrue(
        market.stockOf(commodity) < reference.stock.getValue(commodity),
        "$commodity is untouched by a fire in the fields it comes from"
      )
    }
  }

  @Test
  fun `a campfire is not a siege`() {
    // The scale, stated out loud. Twenty metres of scorched grass against square kilometres of
    // farmland is not a famine, and a model that said otherwise would be wrong in the direction
    // players would notice within a day of finding a torch.
    val before = breadPrice()

    burn(squareMetres = 1_250.0)
    advanceDays(2)

    assertTrue(breadPrice() <= before + 1, "a single campfire moved the price of bread")
  }

  private fun breadPrice(): Double {
    return assertNotNull(service.marketOf(VILLAGE)).priceOf("bread")
  }

  /**
   * What the shop asks for [loaves], in whole coins - the only figure a player is ever shown.
   *
   * A basket rather than a single loaf, because one loaf rounds to the same coin across a move a player
   * would certainly notice on a week's shopping.
   */
  private fun breadQuote(loaves: Int): Long {
    val shop = assertNotNull(service.shopAt(0, 0)).second

    return shop.quoteBuy(catalogue.commodityOrThrow("bread"), loaves).coins
  }

  /**
   * Moves the calendar on, sweeping as it goes - which is what the running server does every thirty
   * real seconds, so a game-day is swept hundreds of times.
   *
   * Jumping the clock and sweeping once at the end is not the same thing and must not be: a settlement
   * with no row yet has no record of when it was last at its reference, so a single sweep can only
   * ever claim one day of damage however far the clock moved. That is the honest answer for a town
   * nobody has looked at, and the reason the sweep runs on a timer rather than on a visit.
   */
  private fun advanceDays(days: Int) {
    repeat(days) {
      now = if (now.day == BestiaDateTime.DAYS_PER_MONTH) {
        now.copy(month = now.month % BestiaDateTime.MONTHS_PER_YEAR + 1, day = 1)
      } else {
        now.copy(day = now.day + 1)
      }
      service.catchUpAll()
    }
  }

  private fun burn(share: Double = 0.0, squareMetres: Double = farmedSquareMetres() * share) {
    val cellsPerColumn = CHUNK * CHUNK
    val wanted = Math.ceil(squareMetres / cellsPerColumn).toInt().coerceAtLeast(1)
    val span = Math.ceil(Math.sqrt(wanted.toDouble())).toInt() + 1

    scars = (-span..span).flatMap { x -> (-span..span).map { y -> x to y } }
      .sortedBy { (x, y) -> x * x + y * y }
      .take(wanted)
      .associate { (x, y) ->
        val key = (x.toLong() shl 32) or (y.toLong() and 0xFFFF_FFFFL)
        key to Scar(ColumnMask(CHUNK).apply { repeat(cellsPerColumn) { set(it) } }, burnedAtSecond = 0)
      }
    damage.invalidate()
  }

  private fun farmedSquareMetres(): Double {
    val radius = Catchment.radiusOf(SettlementTier.VILLAGE, WorldParams().economy)

    return PI * radius * radius * Catchment.MEAN_CLAIM_WEIGHT * (POPULATION / FOOD_CAPACITY) * CEREAL_SHARE
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
      building(100L, "miller", BuildingFunction.CRAFT),
      building(200L, "baker", BuildingFunction.CRAFT),
      building(300L, null, BuildingFunction.FARM),
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
    val START = BestiaDateTime(year = 1, month = 2, day = 3, hour = 12, minute = 0, second = 0)

    /** A week's bread for a household, which is what a player actually carries out of a bakery. */
    const val BASKET = 10

    /** Long enough for the village to have spent what it had put by. */
    const val DAYS_TO_NOTICE = 45

    const val VILLAGE = 12
    const val CHUNK = 32
    const val POPULATION = 300.0
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
