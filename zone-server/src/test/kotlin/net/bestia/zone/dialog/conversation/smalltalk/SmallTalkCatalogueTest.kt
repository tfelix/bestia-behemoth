package net.bestia.zone.dialog.conversation.smalltalk

import net.bestia.worldgen.climate.WeatherKind
import net.bestia.worldgen.place.RegionKind
import net.bestia.worldgen.pop.Kinship
import net.bestia.zone.ai.domain.townsfolk.OccupationCatalogue
import net.bestia.zone.environment.time.Season
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That the shipped pool is usable by everybody, and that a gate means what the yml says it means.
 *
 * The gap worth a test is silence rather than a crash: a trade whose every line happens to be gated on
 * the coast has nothing to say inland, which looks exactly like a working feature until somebody walks
 * into the wrong village.
 */
class SmallTalkCatalogueTest {

  private val catalogue = load()

  @Test
  fun `every trade has something to say wherever and whenever it is asked`() {
    val occupations = OccupationCatalogue().also { it.load() }

    // Swept rather than sampled, because the trap is a combination nobody thought to try: the pool
    // covers all four seasons, so any single spot check finds a line and proves nothing about the rest
    // of the calendar. A silent trade is only ever silent somewhere specific.
    for (trade in occupations.ids()) {
      for (season in Season.entries) {
        for (terrain in RegionKind.entries) {
          for (weather in listOf(WeatherKind.CLEAR, WeatherKind.SNOW, WeatherKind.THUNDERSTORM)) {
            val where = circumstance(occupation = trade, terrain = terrain, season = season, weather = weather)

            assertTrue(
              catalogue.eligible(where).isNotEmpty(),
              "$trade has nothing to say in a $terrain in $season with $weather overhead"
            )
          }
        }
      }
    }
  }

  /**
   * And that the coverage above does not come from the seasonal lines alone.
   *
   * Those cover every day of the year between them, so a pool with no trade lines at all would still
   * pass the sweep. What has to hold is stronger: each trade can reach a line that asks nothing of the
   * world, so deleting one seasonal row cannot silence a quarter of the calendar.
   */
  @Test
  fun `every trade has a line that survives the world changing around it`() {
    val occupations = OccupationCatalogue().also { it.load() }

    for (trade in occupations.ids()) {
      val fallbacks = catalogue.all().filter {
        it.isUnconditional && (it.occupations.isEmpty() || trade in it.occupations)
      }

      assertTrue(fallbacks.isNotEmpty(), "$trade has no line that holds regardless of place and season")
    }
  }

  @Test
  fun `a line names its own trade and nobody else's`() {
    val farmer = catalogue.eligible(circumstance(occupation = "farmer")).map { it.key }
    val guard = catalogue.eligible(circumstance(occupation = "guard")).map { it.key }

    assertTrue(farmer.any { it.contains("FARMER") }, "a farmer was offered no farming line")
    assertFalse(farmer.any { it.contains("GUARD") }, "a farmer was offered a guard's line")
    assertFalse(guard.any { it.contains("FARMER") }, "a guard was offered a farmer's line")
  }

  @Test
  fun `the shared lines wait for the condition they talk about`() {
    val inland = catalogue.eligible(circumstance()).map { it.key }
    val coastal = catalogue.eligible(circumstance(terrain = RegionKind.COAST)).map { it.key }

    assertFalse(inland.contains("TALK_SMALL_COAST_NETS"), "fishing off the point, in a town with no point")
    assertTrue(coastal.contains("TALK_SMALL_COAST_NETS"))
  }

  @Test
  fun `gates within one line are all required`() {
    val line = SmallTalk(
      key = "TEST",
      occupations = setOf("farmer"),
      season = setOf(Season.WINTER),
    )

    assertTrue(line.holdsFor(circumstance(occupation = "farmer", season = Season.WINTER)))
    assertFalse(line.holdsFor(circumstance(occupation = "farmer", season = Season.SUMMER)))
    assertFalse(line.holdsFor(circumstance(occupation = "guard", season = Season.WINTER)))
  }

  @Test
  fun `values within one gate are alternatives`() {
    val line = SmallTalk(key = "TEST", weather = setOf(WeatherKind.RAIN, WeatherKind.HEAVY_RAIN))

    assertTrue(line.holdsFor(circumstance(weather = WeatherKind.RAIN)))
    assertTrue(line.holdsFor(circumstance(weather = WeatherKind.HEAVY_RAIN)))
    assertFalse(line.holdsFor(circumstance(weather = WeatherKind.CLEAR)))
  }

  @Test
  fun `a range gate is inclusive at both ends`() {
    val elder = SmallTalk(key = "TEST", minAge = 60)

    assertFalse(elder.holdsFor(circumstance(age = 59)))
    assertTrue(elder.holdsFor(circumstance(age = 60)))
  }

  /** Every line costs two translated rows, so the ceiling is worth a failing test rather than a comment. */
  @Test
  fun `the pool stays a garnish`() {
    assertTrue(
      catalogue.all().size <= BUDGET,
      "${catalogue.all().size} small-talk lines is past the ${BUDGET} the design budgeted for; " +
        "each one is two rows in every language the game ships"
    )
  }

  @Test
  fun `keys are distinct, because the topic id is a position in this list`() {
    val keys = catalogue.all().map { it.key }

    assertEquals(keys.size, keys.toSet().size, "a duplicate key would make two lines share one topic")
  }

  private fun circumstance(
    occupation: String = "farmer",
    age: Int = 30,
    kinship: Kinship = Kinship.HEAD,
    terrain: RegionKind = RegionKind.DOWNS,
    season: Season = Season.SPRING,
    weather: WeatherKind = WeatherKind.CLEAR,
  ): Circumstance {
    return Circumstance(
      occupation = occupation,
      age = age,
      kinship = kinship,
      terrain = terrain,
      season = season,
      weather = weather,
      walled = false,
      sacked = false,
      wealth = 0.4,
    )
  }

  private fun load(): SmallTalkCatalogue {
    val occupations = OccupationCatalogue().also { it.load() }

    return SmallTalkCatalogue(occupations).also { it.load() }
  }

  private companion object {
    /** Three per occupation plus a dozen shared, with room for one more trade before a rethink. */
    const val BUDGET = 40
  }
}
