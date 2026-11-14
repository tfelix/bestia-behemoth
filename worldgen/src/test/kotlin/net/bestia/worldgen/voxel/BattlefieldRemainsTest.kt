package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.dress.FixtureKind
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import org.junit.jupiter.api.TestInstance
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BattlefieldRemainsTest {

  private val world: GeneratedWorld by lazy {
    TestWorlds.get(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  private val fields: List<PointMarker> by lazy {
    world.world.features.all().filter { it.kind == FeatureKind.BATTLEFIELD }.filterIsInstance<PointMarker>()
  }

  @Test
  fun `the bloodiest battlefield is strewn with bones and broken arms`() {
    val field = fields.maxBy { it.attribute(SiteChannels.FALLEN) }
    val remains = remainsOn(field)
    println("remains on a field of ${field.attribute(SiteChannels.FALLEN).toInt()} dead: $remains")

    assertTrue((remains[FixtureKind.BONES] ?: 0) > 0, "no bones")
    assertTrue((remains[FixtureKind.RUSTED_BLADE] ?: 0) + (remains[FixtureKind.BROKEN_SHIELD] ?: 0) > 0, "no arms")
  }

  @Test
  fun `a world boss's skeleton lies only where it was slain`() {
    val skeletons = fields.associateWith { remainsOn(it)[FixtureKind.GIANT_REMAINS] ?: 0 }
    val slain = fields.filter { it.attribute(SiteChannels.MONSTER) >= 0 }

    assertTrue(slain.isNotEmpty(), "seed 7 should have a world boss slain in its history")
    assertEquals(slain.associateWith { 1 }, skeletons.filterValues { it > 0 })
  }

  private fun remainsOn(field: PointMarker): Map<FixtureKind, Int> {
    val radius = field.attribute(SiteChannels.RADIUS)
    val counts = HashMap<FixtureKind, Int>()
    val config = world.config
    for (cx in config.chunkOf(field.position.x - radius)..config.chunkOf(field.position.x + radius)) {
      for (cy in config.chunkOf(field.position.y - radius)..config.chunkOf(field.position.y + radius)) {
        val props = world.propsIn(cx, cy)
        for (i in props.indices) {
          if (props.kindAt(i) != PropKind.FIXTURE) continue
          if (hypot(props.xAt(i) - field.position.x, props.yAt(i) - field.position.y) > radius) continue
          val kind = FixtureKind.entries[props.subKindAt(i)]
          if (kind in REMAINS) counts[kind] = (counts[kind] ?: 0) + 1
        }
      }
    }
    return counts
  }

  private companion object {
    val REMAINS = setOf(
      FixtureKind.BONES,
      FixtureKind.RUSTED_BLADE,
      FixtureKind.BROKEN_SHIELD,
      FixtureKind.BANNER_RAG,
      FixtureKind.GIANT_REMAINS
    )
  }
}
