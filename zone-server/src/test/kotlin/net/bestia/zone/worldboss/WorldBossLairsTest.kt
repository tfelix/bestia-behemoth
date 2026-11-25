package net.bestia.zone.worldboss

import net.bestia.worldgen.core.MonsterFate
import net.bestia.worldgen.core.WorldBoss
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.vector.Vec2d
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldBossLairsTest {

  private val bestiaIds = WorldBoss.entries.associate { it.identifier to 101L + it.ordinal }

  @Test
  fun `every boss that withdrew or vanished gets a den holding its species`() {
    val chronicle = world.world.chronicle
    val hiding = chronicle.monsters.filter { it.fate == MonsterFate.WITHDRAWN || it.fate == MonsterFate.VANISHED }

    val lairs = WorldBossLairService.lairsOf(world, WORLD_ID, bestiaIds::get)

    assertTrue(lairs.isNotEmpty(), "the test world has no lair to check")
    assertEquals(hiding.map { it.index }.sorted(), lairs.map { it.monster }.sorted())
    for (lair in lairs) {
      assertEquals(bestiaIds.getValue(chronicle.monsters[lair.monster].boss.identifier), lair.bestiaId)
      assertEquals(WORLD_ID, lair.identity.worldId)
    }
  }

  @Test
  fun `a spot at sea finds a dry shore nearby`() {
    val sea = seaSpot()
    assumeTrue(sea != null, "the test world has no sea")

    val shore = assertNotNull(WorldBossLairService.shoreNear(world, sea!!))

    assertTrue(world.base.heightAt(shore.x, shore.y) > world.config.seaLevel)
  }

  @Test
  fun `a lair whose species is missing gets no den`() {
    assertEquals(emptyList(), WorldBossLairService.lairsOf(world, WORLD_ID) { null })
  }

  /** The first cell of the test world that lies under the sea, scanning a coarse grid. */
  private fun seaSpot(): Vec2d? {
    val size = world.config.widthCells * world.config.baseResolution.metresPerCell
    for (x in 0 until size.toInt() step 64) {
      for (y in 0 until size.toInt() step 64) {
        if (world.base.heightAt(x.toDouble(), y.toDouble()) < world.config.seaLevel - 5) return Vec2d(x.toDouble(), y.toDouble())
      }
    }
    return null
  }

  private companion object {
    const val WORLD_ID = 7L

    val world: GeneratedWorld
      get() = TestWorlds.get(WorldConfig(seed = 9001L, widthCells = 48, heightCells = 48))
  }
}
