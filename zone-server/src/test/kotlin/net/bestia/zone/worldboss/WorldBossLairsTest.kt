package net.bestia.zone.worldboss

import net.bestia.worldgen.core.MonsterFate
import net.bestia.worldgen.core.WorldBoss
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import org.junit.jupiter.api.Test
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
  fun `a lair whose species is missing gets no den`() {
    assertEquals(emptyList(), WorldBossLairService.lairsOf(world, WORLD_ID) { null })
  }

  private companion object {
    const val WORLD_ID = 7L

    val world: GeneratedWorld
      get() = TestWorlds.get(WorldConfig(seed = 9001L, widthCells = 48, heightCells = 48))
  }
}
