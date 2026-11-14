package net.bestia.zone.spawn.ecs

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.zone.battle.BestiaKind
import net.bestia.zone.bestia.persistence.Bestia
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BattlefieldHauntsTest {

  @Test
  fun `every battlefield gets one night den`() {
    val fields = world.world.features.all().filter { it.kind == FeatureKind.BATTLEFIELD }.map { it.id.value }

    val haunts = BattlefieldHaunts.hauntsOf(world, WORLD_ID, undeadAtEveryLevel())

    assertTrue(fields.isNotEmpty(), "the test world has no battlefield to check")
    assertEquals(fields.sorted(), haunts.map { it.identity.featureId }.sorted())
  }

  @Test
  fun `only the dead and the battlefield species haunt a field`() {
    val beasts = undeadAtEveryLevel().onEach { it.kind = BestiaKind.BEAST }
    assertEquals(emptyList(), BattlefieldHaunts.hauntsOf(world, WORLD_ID, beasts))

    beasts.forEach { it.battlefield = true }
    assertTrue(BattlefieldHaunts.hauntsOf(world, WORLD_ID, beasts).isNotEmpty())
  }

  @Test
  fun `no boss haunts a field`() {
    val bosses = undeadAtEveryLevel().onEach { it.boss = true }

    assertEquals(emptyList(), BattlefieldHaunts.hauntsOf(world, WORLD_ID, bosses))
  }

  private fun undeadAtEveryLevel() = (1..25).map { undead(id = it.toLong(), level = it * 5) }

  private fun undead(id: Long, level: Int) = Bestia(
    id = id,
    identifier = "undead_" + "x".repeat(id.toInt()),
    level = level,
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
