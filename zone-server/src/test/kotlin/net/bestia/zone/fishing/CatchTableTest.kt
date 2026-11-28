package net.bestia.zone.fishing

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.zone.navigation.region.WaterKind
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatchTableTest {

  private val boot = CatchTable.Entry(itemId = 1, level = 1, weight = 40)
  private val perch = CatchTable.Entry(itemId = 2, level = 8, weight = 100)
  private val eel = CatchTable.Entry(itemId = 3, level = 32, weight = 100)
  private val table = CatchTable(listOf(boot, perch, eel), overCapHalvingLevels = 5)

  @Test
  fun `an entry up to the cap keeps its weight`() {
    assertEquals(100.0, table.weightOf(perch, levelCap = 8))
  }

  @Test
  fun `above the cap the weight halves every five levels`() {
    assertEquals(25.0, table.weightOf(perch, levelCap = -2))
    assertEquals(100.0 * Math.pow(0.5, 22 / 5.0), table.weightOf(eel, levelCap = 10), 1e-9)
  }

  @Test
  fun `the rod's max catch level keeps higher entries from ever biting`() {
    val random = Random(42)
    val drawn = (1..2_000).mapNotNull { table.draw(maxCatchLevel = 25, levelCap = 100, random) }

    assertEquals(setOf(boot, perch), drawn.toSet())
  }

  @Test
  fun `a rod below every entry catches nothing`() {
    assertNull(table.draw(maxCatchLevel = 0, levelCap = 100, Random(1)))
  }

  @Test
  fun `the draw follows the weights`() {
    val draws = 20_000
    val random = Random(42)
    val perches = (1..draws).count { table.draw(maxCatchLevel = 100, levelCap = 100, random) == perch }

    // 100 of 240 weight.
    assertEquals(100.0 / 240.0, perches / draws.toDouble(), 0.02)
  }

  @Test
  fun `a catch bites only in its own waters`() {
    val herring = CatchTable.Entry(itemId = 4, level = 1, weight = 100, waters = setOf(WaterKind.SEA))

    assertTrue(herring.bitesAt(spot(water = WaterKind.SEA)))
    assertFalse(herring.bitesAt(spot(water = WaterKind.LAKE)))
  }

  @Test
  fun `biome, weather, hour and corruption each refuse a catch`() {
    val char = CatchTable.Entry(itemId = 5, level = 1, weight = 100, biomes = setOf(Biome.TUNDRA))
    val ray = CatchTable.Entry(itemId = 6, level = 1, weight = 100, weather = setOf(WeatherKind.THUNDERSTORM))
    val moonscale = CatchTable.Entry(itemId = 7, level = 1, weight = 100, night = true)
    val voidgill = CatchTable.Entry(itemId = 8, level = 1, weight = 100, corruptedOnly = true)

    assertFalse(char.bitesAt(spot(biome = Biome.GRASSLAND)))
    assertTrue(char.bitesAt(spot(biome = Biome.TUNDRA)))
    assertFalse(ray.bitesAt(spot(weather = WeatherKind.RAIN)))
    assertTrue(ray.bitesAt(spot(weather = WeatherKind.THUNDERSTORM)))
    assertFalse(moonscale.bitesAt(spot(isNight = false)))
    assertTrue(moonscale.bitesAt(spot(isNight = true)))
    assertFalse(voidgill.bitesAt(spot(corrupted = false)))
    assertTrue(voidgill.bitesAt(spot(corrupted = true)))
  }

  @Test
  fun `what the spot does not know refuses nothing`() {
    val ray = CatchTable.Entry(itemId = 6, level = 1, weight = 100, waters = setOf(WaterKind.SEA), weather = setOf(WeatherKind.THUNDERSTORM))

    assertTrue(ray.bitesAt(FishingSpot.UNKNOWN))
  }

  @Test
  fun `weather, night, bait and the cap multiply together`() {
    val herring = CatchTable.Entry(
      itemId = 4, level = 13, weight = 10, boostWeather = setOf(WeatherKind.RAIN), weatherBoost = 3.0, nightBoost = 2.0,
      tags = setOf(CatchTag.SHOAL),
    )
    val rainyNight = spot(weather = WeatherKind.RAIN, isNight = true)

    // 10 * 3 (rain) * 2 (night) * 2 (chum) * 0.5 (five levels over the cap).
    assertEquals(60.0, table.weightOf(herring, levelCap = 8, rainyNight, mapOf(CatchTag.SHOAL to 2.0)), 1e-9)
    assertEquals(10.0 * 0.5, table.weightOf(herring, levelCap = 8, spot(weather = WeatherKind.CLEAR, isNight = false)), 1e-9)
  }

  @Test
  fun `a spot that refuses every entry catches nothing`() {
    val seaOnly = CatchTable(listOf(CatchTable.Entry(itemId = 4, level = 1, weight = 1, waters = setOf(WaterKind.SEA))), 5)

    assertNull(seaOnly.draw(maxCatchLevel = 100, levelCap = 100, Random(1), spot(water = WaterKind.POND)))
  }

  private fun spot(
    water: WaterKind? = null,
    biome: Biome? = null,
    weather: WeatherKind? = null,
    isNight: Boolean? = null,
    corrupted: Boolean? = null,
  ) = FishingSpot(water, biome, weather, isNight, corrupted)
}
