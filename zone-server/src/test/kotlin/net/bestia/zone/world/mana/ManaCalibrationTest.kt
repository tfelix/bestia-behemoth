package net.bestia.zone.world.mana

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.mana.ManaField.Cell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Replays the play the emitter numbers in [ManaEmissionConfig] are set from, with the real field rule, and checks
 * the field feels as the world events design asks. Change a number here first, then the config to match.
 */
class ManaCalibrationTest {

  private val field = ManaField()
  private val config = ManaEmissionConfig()
  private val emission = ManaEmission(field, config)
  private val rule = ManaFieldRule(ManaFieldConfig(), realSecondsPerBestiaDay = DAY / 3)

  private val ordinaryLand = object : ManaFieldRule.Ground {
    override fun startDensity(cell: Cell) = 0.13
    override fun isSanctuary(cell: Cell) = false
  }

  @Test
  fun `a caster spending a day at one spot raises it by about 0_03`() {
    val castsPerMinute = CASTER_DAY_MANA / CASTING_MINUTES / SPELL_COST

    playDay(activeMinutes = CASTING_MINUTES) {
      repeat(castsPerMinute.toInt()) { emission.onCast(SPOT, SPELL_COST.toInt()) }
    }

    assertEquals(0.03, field.deltaAt(SPOT.x.toDouble(), SPOT.y.toDouble()), 0.01)
  }

  @Test
  fun `a grind spot hunted every day for a week rises by about 0_15`() {
    val random = Random(7)
    val killsPerMinute = GRIND_KILLS_PER_DAY / GRINDING_MINUTES

    repeat(7) {
      playDay(activeMinutes = GRINDING_MINUTES) {
        repeat(killsPerMinute) { emission.onKill(randomSpotWithin(random, GRIND_RADIUS), GRIND_LEVEL, boss = false) }
      }
    }

    assertEquals(0.15, meanDeltaWithin(GRIND_RADIUS), 0.04)
  }

  @Test
  fun `the land around an old Wound reaches storms in about two weeks`() {
    val wound = WoundFootprint(SPOT.x.toDouble(), SPOT.y.toDouble(), config.woundReachMetres, field)
    val motesPerMinute = WoundSources.motesPerSecond(config, ageDays = config.woundFullAgeDays) * MINUTE

    repeat(14 * MINUTES_PER_DAY) {
      wound.pour(field, motesPerMinute)
      rule.step(field, ordinaryLand, MINUTE)
    }

    assertEquals(STORMS - 0.13, meanDeltaWithin(config.woundReachMetres), 0.05)
  }

  @Test
  fun `a young Wound pours a tenth of an old one`() {
    val young = WoundSources.motesPerSecond(config, ageDays = 0.0)
    val old = WoundSources.motesPerSecond(config, ageDays = 365.0)

    assertEquals(0.1, young / old, 1e-9)
  }

  /** Runs one real day: [act] every minute for the first [activeMinutes], then the field alone. */
  private fun playDay(activeMinutes: Int, act: () -> Unit) {
    repeat(MINUTES_PER_DAY) { minute ->
      if (minute < activeMinutes) act()
      rule.step(field, ordinaryLand, MINUTE)
    }
  }

  private fun randomSpotWithin(random: Random, radius: Double): Vec3L {
    val distance = radius * sqrt(random.nextDouble())
    val angle = random.nextDouble() * 2 * Math.PI
    return Vec3L((SPOT.x + distance * cos(angle)).toLong(), (SPOT.y + distance * sin(angle)).toLong(), 0)
  }

  private fun meanDeltaWithin(radius: Double): Double {
    val cells = field.cellsWithin(SPOT.x.toDouble(), SPOT.y.toDouble(), radius)
    return cells.sumOf { field.deltaOf(it.x, it.y) } / cells.size
  }

  private companion object {
    const val DAY = 86_400.0
    const val MINUTE = 60.0
    const val MINUTES_PER_DAY = 24 * 60

    /** In the middle of a tile, far from any edge. */
    val SPOT = Vec3L(2050, 2050, 0)

    /** A caster's day: four hours of casting, about what their regeneration and a few potions allow. */
    const val CASTER_DAY_MANA = 20_000.0
    const val CASTING_MINUTES = 4 * 60
    const val SPELL_COST = 25.0

    /** A grinder's day: two hours at a kill every twenty seconds, at a spot about 300 m across. */
    const val GRIND_KILLS_PER_DAY = 360
    const val GRINDING_MINUTES = 2 * 60
    const val GRIND_LEVEL = 30
    const val GRIND_RADIUS = 150.0

    const val STORMS = 0.60
  }
}
