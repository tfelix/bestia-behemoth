package net.bestia.zone.world.fire

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.worldgen.climate.WeatherModel
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.weather.WeatherAt
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The automaton, against a fake ground and a fake sky.
 *
 * **Every spreading test is fenced onto a small island of fuel**, and that is not only about realism. The
 * collaborators here are relaxed mockks, which record every invocation and retain its arguments - so a fire
 * left to spread across an unbounded plain accumulates tens of thousands of retained `ColumnMask`s and takes
 * the test JVM down with it. Fence the fuel, and the fire is bounded by the world rather than by the harness.
 *
 * Both `BurnableGround` and the weather are stubbed, and that is the whole reason `BurnableGround` is a
 * `fun interface` - `ForageGround`'s KDoc makes the same argument: *"faking a biome raster to ask 'does a
 * hungry deer walk to the grass' is a great deal of machinery for a question with a boolean answer"*. Asking
 * "does a fire run downwind" of a generated world would measure the world.
 *
 * Every roll goes through `GenRng.hashUnit` over `(seed, fireId, cell, stepIndex)`, so these are exact rather
 * than statistical: the same seed gives the same fire every time.
 */
class GroundFireServiceTest {

  private val chunkSize = 32
  private val seed = 4242L

  private lateinit var scorch: ScorchRegistry
  private lateinit var overlay: GroundOverlayService

  /** East is bearing 0; `windDirection` is the direction of travel, so this blows towards +x. */
  private val east = 0.0
  private val north = PI / 2

  private fun service(
    fuelEverywhere: Double = 0.8,
    windSpeed: Double = 0.0,
    windDirection: Double = east,
    intensity: Double = 0.0,
    dryness: Double = 0.8,
    kind: WeatherKind = WeatherKind.CLEAR,
    fuel: BurnableGround = BurnableGround { _, _ -> fuelEverywhere },
    config: GroundFireConfig = GroundFireConfig(),
  ): GroundFireService {
    val worldService = mockk<WorldService>(relaxed = true) {
      every { this@mockk.config } returns mockk { every { this@mockk.chunkSize } returns 32 }
      every { record } returns mockk {
        every { this@mockk.seed } returns this@GroundFireServiceTest.seed
        every { shapeVersion } returns 1L
        every { pipelineVersion } returns 1L
      }
    }

    val executor = mockk<AsyncJobExecutor> {
      every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    }

    scorch = ScorchRegistry(
      mockk(relaxed = true) {
        every { findAll() } returns emptyList()
        every { save(any<ScorchMark>()) } answers { firstArg() }
      },
      executor,
      worldService
    )

    val weather = mockk<net.bestia.zone.environment.weather.WeatherService> {
      every { at(any(), any(), any(), any()) } returns WeatherAt(
        regionId = 1,
        state = WeatherModel.let {
          net.bestia.worldgen.climate.WeatherState(
            kind = kind,
            intensity = intensity,
            cloudCover = 0.0,
            windSpeed = windSpeed,
            windDirection = windDirection,
            dryness = dryness
          )
        },
        temperature = net.bestia.worldgen.climate.Temperature(15.0)
      )
    }

    val clock = mockk<BestiaClock> {
      every { now() } returns mockk(relaxed = true) { every { absoluteSecond } returns 1_000L }
    }

    overlay = mockk(relaxed = true)

    return GroundFireService(
      config = config,
      fuel = fuel,
      weather = weather,
      scorch = scorch,
      overlay = overlay,
      damage = mockk(relaxed = true),
      worldService = worldService,
      clock = clock
    )
  }

  private fun ignite(sut: GroundFireService, at: Vec3L = Vec3L(0, 0, 0), radius: Long = 0) =
    sut.ignite(at, radius, casterId = 1L, skillId = 1000L, skillLevel = 1)

  private fun run(sut: GroundFireService, steps: Int, config: GroundFireConfig = GroundFireConfig()) {
    val world = testWorld()
    repeat(steps) { sut.step(world, config.stepSeconds) }
  }

  /** Fuel on a square island, so a spreading test cannot run away across an infinite plain. */
  private fun island(half: Long, value: Double = 0.8) =
    BurnableGround { x, y -> if (x in -half..half && y in -half..half) value else 0.0 }

  @Test
  fun `a fire on unburnable ground does not start`() {
    val sut = service(fuelEverywhere = 0.0)

    assertNull(ignite(sut), "a fire started on ground with no fuel")
    assertEquals(0, sut.activeFires)
  }

  /**
   * Termination, on a bounded island of fuel.
   *
   * Bounded rather than fuel-everywhere on purpose, and not only for speed: "it eventually stops" is only a
   * meaningful claim if there is something to stop it. On infinite fuel the honest answer is that a grass fire
   * runs until it hits the cell cap, which is a different property and is tested separately.
   */
  @Test
  fun `a fire on an island of fuel burns it out and stops`() {
    val half = 8L
    val sut = service(fuel = island(half))
    assertNotNull(ignite(sut))

    run(sut, steps = 4)
    assertTrue(sut.burningCells > 1, "the fire never spread past its ignition cell")

    // Its own scorch is what stops it re-entering ground it has burnt, so it terminates rather than pulsing.
    run(sut, steps = 80)
    assertEquals(0, sut.activeFires, "the fire never went out: ${sut.burningCells} cells still alight")
    assertTrue(scorch.scarredColumns > 0, "the fire left no scar behind")
    assertTrue(
      burntCells().all { it.first in -half..half && it.second in -half..half },
      "the fire burnt ground that had no fuel"
    )
  }

  /** The per-fire cell cap stops it *igniting* but not burning down what it holds, so it still ends. */
  @Test
  fun `the per-fire cell cap stops spread without freezing the fire`() {
    val config = GroundFireConfig(maxBurningCellsPerFire = 200)
    val sut = service(config = config, fuel = island(14))
    assertNotNull(ignite(sut))

    run(sut, steps = 30, config = config)

    assertTrue(burntCells().isNotEmpty(), "the capped fire scorched nothing")
    assertTrue(
      burntCells().size <= 200,
      "the cap let ${burntCells().size} cells burn against a limit of 200"
    )
  }

  /** **The wind test.** Without it, the whole point of reading a bearing is unverified. */
  @Test
  fun `a strong wind stretches the burn downwind`() {
    val sut = service(windSpeed = 25.0, windDirection = east, fuel = island(10))
    assertNotNull(ignite(sut))
    run(sut, steps = 12)

    val cells = burntCells()
    val eastward = cells.filter { it.first > 0 }.size
    val westward = cells.filter { it.first < 0 }.size

    assertTrue(eastward > 0, "the fire did not spread at all")
    assertTrue(
      eastward > westward * 2,
      "an easterly gale spread $eastward cells east and $westward west; wind is not steering the fire"
    )
  }

  @Test
  fun `the wind bearing decides which way, not just that it stretches`() {
    val eastern = service(windSpeed = 25.0, windDirection = east, fuel = island(10))
    assertNotNull(ignite(eastern))
    run(eastern, steps = 12)
    val eastRun = burntCells().maxOfOrNull { it.first } ?: 0L

    val northern = service(windSpeed = 25.0, windDirection = north, fuel = island(10))
    assertNotNull(ignite(northern))
    run(northern, steps = 12)
    val northRun = burntCells().maxOfOrNull { it.second } ?: 0L

    assertTrue(eastRun > 2, "an easterly gale reached only $eastRun cells east")
    assertTrue(northRun > 2, "a northerly gale reached only $northRun cells north")
  }

  @Test
  fun `a strip of bare ground is not crossed`() {
    // A four-tile firebreak at x in 3..6, cut through an island - bounded in every other direction too, or the
    // fire runs off across an infinite plain and the test measures how long a JVM survives that.
    val half = 10L
    val sut = service(
      windSpeed = 25.0,
      windDirection = east,
      fuel = BurnableGround { x, y ->
        when {
          x !in -half..half || y !in -half..half -> 0.0
          x in 3L..6L -> 0.0
          else -> 0.8
        }
      }
    )
    assertNotNull(ignite(sut))
    run(sut, steps = 40)

    assertTrue(
      burntCells().none { it.first > 6L },
      "the fire crossed a four-tile gap with no fuel in it: ${burntCells().filter { it.first > 6L }}"
    )
  }

  @Test
  fun `a downpour puts a fire out in one step`() {
    val sut = service(intensity = 1.0, kind = WeatherKind.HEAVY_RAIN)
    assertNotNull(ignite(sut, radius = 2))
    assertTrue(sut.burningCells > 0)

    run(sut, steps = 1)

    assertEquals(0, sut.activeFires, "a downpour did not extinguish the fire")
  }

  @Test
  fun `rain short of a downpour suppresses spread without extinguishing`() {
    // Live cells, not scorch: at six steps against `burnSteps` of eight nothing has burnt *out* yet, so
    // counting scars would compare zero with zero and pass for the wrong reason.
    val wet = service(intensity = 0.4, kind = WeatherKind.RAIN, fuel = island(10))
    assertNotNull(ignite(wet, radius = 1))
    run(wet, steps = 6)
    val wetSpread = wet.burningCells

    val dry = service(intensity = 0.0, fuel = island(10))
    assertNotNull(ignite(dry, radius = 1))
    run(dry, steps = 6)
    val drySpread = dry.burningCells

    assertTrue(drySpread > wetSpread, "rain ($wetSpread cells) did not slow the fire against dry ($drySpread)")
  }

  @Test
  fun `the concurrent-fire cap refuses rather than merges`() {
    val config = GroundFireConfig(maxConcurrentFires = 2)
    val sut = service(config = config)

    assertNotNull(sut.ignite(Vec3L(0, 0, 0), 0, 1L, 1000L, 1))
    assertNotNull(sut.ignite(Vec3L(100, 100, 0), 0, 1L, 1000L, 1))
    assertNull(sut.ignite(Vec3L(200, 200, 0), 0, 1L, 1000L, 1), "the cap let a third fire through")
    assertEquals(2, sut.activeFires)
  }

  /** Determinism is what makes every test above exact rather than statistical. */
  @Test
  fun `two fires from the same seed burn identically`() {
    val first = service(windSpeed = 15.0, fuel = island(10))
    assertNotNull(ignite(first))
    run(first, steps = 10)
    val a = burntCells()

    val second = service(windSpeed = 15.0, fuel = island(10))
    assertNotNull(ignite(second))
    run(second, steps = 10)
    val b = burntCells()

    assertEquals(a, b, "the same seed produced two different fires")
  }

  /** Every scorched cell, as `(voxelX, voxelY)`, read back out of the store. */
  private fun burntCells(): Set<Pair<Long, Long>> {
    val out = HashSet<Pair<Long, Long>>()
    for (column in scorch.scarredKeys()) {
      val originX = ScorchRegistry.chunkXOf(column).toLong() * chunkSize
      val originY = ScorchRegistry.chunkYOf(column).toLong() * chunkSize
      scorch.scarOf(column)?.mask?.forEachSet { x, y -> out.add(originX + x to originY + y) }
    }
    return out
  }
}
