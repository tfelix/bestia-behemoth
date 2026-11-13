package net.bestia.zone.worldboss

import net.bestia.worldgen.core.MonsterFate
import net.bestia.worldgen.core.WorldBoss
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.dress.FixtureKind
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.voxel.PropKind
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldBossSealsTest {

  private val bestiaIds = WorldBoss.entries.associate { it.identifier to 101L + it.ordinal }

  @Test
  fun `every seal stands a crystal whose prop id names the sealed boss`() {
    val sealed = world.world.chronicle.monsters.filter { it.fate == MonsterFate.SEALED }
    val bossByCrystal = WorldBossSealService.bossesBySealCrystal(world, bestiaIds::get)

    assertTrue(sealed.isNotEmpty(), "the test world has no seal to check")
    assertEquals(sealed.map { bestiaIds.getValue(it.boss.identifier) }.sorted(), bossByCrystal.values.sorted())

    for (seal in world.world.features.all().filter { it.kind == FeatureKind.SEAL }) {
      val props = world.propsIn(world.config.chunkOf(seal.bbox.centerX), world.config.chunkOf(seal.bbox.centerY))
      val crystals = props.indices.filter {
        props.kindAt(it) == PropKind.FIXTURE && props.subKindAt(it) == FixtureKind.SEAL_CRYSTAL.ordinal
      }

      assertEquals(1, crystals.size, "seal ${seal.id} stands no crystal, or several")
      assertTrue(props.identityAt(crystals.single()) in bossByCrystal, "seal ${seal.id}'s crystal names no boss")
    }
  }

  private companion object {
    val world: GeneratedWorld
      get() = TestWorlds.get(WorldConfig(seed = 9001L, widthCells = 48, heightCells = 48))
  }
}
