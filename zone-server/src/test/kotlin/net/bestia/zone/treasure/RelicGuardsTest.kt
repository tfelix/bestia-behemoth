package net.bestia.zone.treasure

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.bestia.persistence.Bestia
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelicGuardsTest {

  @Test
  fun `every tomb and monument with an artifact is guarded, and no other`() {
    val withArtifact = world.world.features.all()
      .filter { it.kind == FeatureKind.TOMB || it.kind == FeatureKind.MONUMENT }
      .filterIsInstance<PointMarker>()
      .filter { it.attribute(SiteChannels.ARTIFACT) >= 0 }
      .map { it.id.value }

    val guards = RelicGuards.guardsOf(world, WORLD_ID, undeadAtEveryLevel())

    assertTrue(withArtifact.isNotEmpty(), "the test world has no artifact tomb to check")
    assertEquals(withArtifact.sorted(), guards.map { it.identity.featureId }.sorted())
  }

  @Test
  fun `a site no undead fits stays unguarded`() {
    val farTooStrong = listOf(undead(id = 1, level = 200))

    assertEquals(emptyList(), RelicGuards.guardsOf(world, WORLD_ID, farTooStrong))
  }

  @Test
  fun `a boss guards alone and anything less comes as a band`() {
    val bosses = undeadAtEveryLevel().map { it.also { species -> species.boss = true } }

    assertTrue(RelicGuards.guardsOf(world, WORLD_ID, bosses).all { it.pack == 1 })
    assertTrue(RelicGuards.guardsOf(world, WORLD_ID, undeadAtEveryLevel()).all { it.pack > 1 })
  }

  private fun undeadAtEveryLevel() = (1..20).map { undead(id = it.toLong(), level = it * 5) }

  private fun undead(id: Long, level: Int) = Bestia(
    id = id,
    identifier = "undead_" + "x".repeat(id.toInt()),
    levelMin = level,
    levelMax = level,
    experienceReward = 1,
    health = 10,
    mana = 10,
    kind = BestiaKind.UNDEAD,
  )

  private companion object {
    const val WORLD_ID = 7L

    val world: GeneratedWorld
      get() = TestWorlds.get(WorldConfig(seed = 9001L, widthCells = 48, heightCells = 48))
  }
}
