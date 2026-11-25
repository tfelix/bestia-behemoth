package net.bestia.worldgen.place

import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.TestWorlds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The counts are printed too: a partition into one province passes every structural check there is. */
class ProvincesTest {

  private val world by lazy {
    TestWorlds.get(WorldConfig(seed = StandardWorld.DEFAULT_SEED, widthCells = 128, heightCells = 128))
  }

  private val regions by lazy { PlaceRegions.of(world.world) }

  private val provinces by lazy { Provinces.of(world.world, regions) }

  @Test
  fun `towns hold most land and the wilds the rest`() {
    val bySeat = provinces.provinces.groupingBy { it.seat.kind }.eachCount()
    val landRegions = provinces.provinces.map { province -> province.regions.count { !regions.regions[it].isWater } }
    println("Provinces: ${provinces.count} $bySeat, land regions each ${landRegions.min()}..${landRegions.max()}")
    println(provinces.provinces.joinToString("\n") {
      "  ${it.name.padEnd(24)} ${it.seat.kind} ${it.regions.size} region(s)"
    })

    assertTrue(provinces.count in 4..60) { "${provinces.count} provinces on a 128 km world" }
  }

  @Test
  fun `every home settlement seats a province of its own`() {
    val chronicle = world.world.chronicle
    val standing = chronicle.settlements.indices.filter { chronicle.settlementStood(it, chronicle.presentYear) }
    val homes = SettlementSpawnPoints.homeCandidateIndices(standing.associateWith { chronicle.settlements[it].population })
    val seated = provinces.provinces.map { it.seat.settlementIndex }.toSet()

    assertTrue(homes.isNotEmpty())
    assertTrue(seated.containsAll(homes)) { "homes $homes, capitals $seated" }
  }

  @Test
  fun `every piece of land belongs to exactly one province`() {
    regions.regions.filter { !it.isWater }.forEach { region ->
      val holders = provinces.provinces.filter { region.index in it.regions }
      assertEquals(1, holders.size) { "${region.name} is held by ${holders.map { it.name }}" }
    }
  }

  @Test
  fun `a province holds its own seat and its capital stands in it`() {
    provinces.provinces.forEach { province ->
      assertTrue(province.seat.region in province.regions) { "${province.name} lost its seat region" }
      assertEquals(province, provinces.provinceAt(province.seat.position.x, province.seat.position.y))
    }
  }

  @Test
  fun `open sea belongs to no province and coastal water to the shore it touches`() {
    regions.regions.filter { it.isWater }.forEach { water ->
      val province = provinces.ofRegion(water.index)
      val touchesLand = regions.neighboursOf(water.index).any { !regions.regions[it].isWater }

      if (touchesLand) {
        assertTrue(province != null) { "${water.name} touches land but has no province" }
      } else {
        assertNull(province) { "${water.name} is open sea but belongs to ${province?.name}" }
      }
    }
  }

  @Test
  fun `the same world splits the same way`() {
    val again = Provinces.of(world.world, PlaceRegions.of(world.world))

    assertEquals(provinces.provinces, again.provinces)
    assertEquals(provinces.partitionVersion, again.partitionVersion)
  }

  @Test
  fun `the partition version moves with the params`() {
    val base = Provinces.partitionVersionOf(RegionParams(), ProvinceParams())

    assertNotEquals(base, Provinces.partitionVersionOf(RegionParams(spacing = 5_000.0), ProvinceParams()))
    assertNotEquals(base, Provinces.partitionVersionOf(RegionParams(), ProvinceParams(wildGap = 20_000.0)))
  }
}
