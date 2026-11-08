package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureKind
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Boulders, loose stones and sticks per hectare over a spread of dry land, and the places none may lie.
 *
 * Counted output, on the habit [GroundCoverScatterTest] states: a density an order of magnitude off in either
 * direction passes every structural test, and only a census shows it. The bounds are wide tripwires around the
 * printed figures.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LootScatterTest {

  private val world: GeneratedWorld by lazy {
    TestWorlds.get(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  private class Item(val kind: PropKind, val id: Long, val x: Double, val y: Double, val ground: Double)

  /** Chunks centred on a stride of dry land cells across the whole world. */
  private val sweptChunks: Set<Pair<Int, Int>> by lazy {
    val elevation: FloatLayer = world.world.layers.require(LayerId.ELEVATION)
    val water: FloatLayer = world.world.layers.require(LayerId.WATER_LEVEL)
    val region = elevation.region
    val metres = world.config.baseResolution.metresPerCell

    val dry = elevation.data.indices.filter {
      elevation.data[it] > world.config.seaLevel && water.data[it].isNaN()
    }
    val stride = (dry.size / SAMPLES).coerceAtLeast(1)

    val chunks = LinkedHashSet<Pair<Int, Int>>()
    for (k in 0 until SAMPLES) {
      val index = dry.getOrNull(k * stride) ?: break
      val centreX = (region.minX + index % region.width + 0.5) * metres
      val centreY = (region.minY + index / region.width + 0.5) * metres
      val chunkX = world.config.chunkOf(centreX)
      val chunkY = world.config.chunkOf(centreY)
      for (dy in 0 until BLOCK) for (dx in 0 until BLOCK) chunks.add((chunkX + dx) to (chunkY + dy))
    }
    chunks
  }

  private val items: List<Item> by lazy {
    val out = ArrayList<Item>()
    for ((chunkX, chunkY) in sweptChunks) {
      val props = world.propsIn(chunkX, chunkY)
      for (i in props.indices) {
        if (props.kindAt(i) !in LootScatter.KINDS) continue
        out.add(Item(props.kindAt(i), props.identityAt(i), props.xAt(i), props.yAt(i), props.groundAt(i)))
      }
    }
    out
  }

  @Test
  fun `the census reads sensibly`() {
    val hectares = sweptChunks.size * world.config.chunkExtent * world.config.chunkExtent / 10_000.0
    val perHectare = LootScatter.KINDS.associateWith { kind -> items.count { it.kind == kind } / hectares }

    println("loot over ${sweptChunks.size} chunks (%.0f ha):".format(hectares))
    for ((kind, rate) in perHectare) println("  %-12s %6.2f per hectare".format(kind.name, rate))

    // A stone within a short walk of anywhere dry is the promise; a stone every few metres is an entity flood.
    val stones = perHectare.getValue(PropKind.LOOSE_STONE)
    assertTrue(stones in 1.0..60.0, "loose stones at %.2f per hectare".format(stones))

    val boulders = perHectare.getValue(PropKind.BOULDER)
    assertTrue(boulders in 0.2..60.0, "boulders at %.2f per hectare".format(boulders))

    val sticks = perHectare.getValue(PropKind.STICK)
    assertTrue(sticks in 0.2..80.0, "sticks at %.2f per hectare".format(sticks))
  }

  @Test
  fun `a stick lies only under trees`() {
    val surface = world.materializer.surface
    val sticks = items.filter { it.kind == PropKind.STICK }
    assertTrue(sticks.isNotEmpty(), "no sticks at all, so this proves nothing")
    for (stick in sticks) {
      assertTrue(surface.biomeAt(stick.x, stick.y).canopy > 0.0, "a stick at (${stick.x}, ${stick.y}) has no canopy")
    }
  }

  @Test
  fun `nothing lies in a worked field or in standing water, and no item is claimed twice`() {
    val surface = world.materializer.surface
    assertTrue(items.isNotEmpty(), "the sweep found nothing, so this proves nothing")

    val seen = HashSet<Long>()
    for (item in items) {
      assertTrue(seen.add(item.id), "${item.kind} ${item.id} is claimed by two chunks")
      assertTrue(surface.waterLevelAt(item.x, item.y) <= item.ground, "${item.kind} lies under water")

      val fields = world.world.features.query(
        net.bestia.worldgen.vector.Aabb(item.x - 1.0, item.y - 1.0, item.x + 1.0, item.y + 1.0)
      ).filter { it.kind == FeatureKind.FIELD }.filterIsInstance<AreaFeature>()
      assertTrue(fields.none { it.contains(item.x, item.y) }, "${item.kind} lies in a field at (${item.x}, ${item.y})")
    }
  }

  private companion object {
    const val SAMPLES = 24
    const val BLOCK = 3
  }
}
