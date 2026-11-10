package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
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
 * Boulders, loose stones, sticks and resin per hectare over a spread of dry land, and the places none may lie.
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
    chunksAround(elevation.data.indices.filter {
      elevation.data[it] > world.config.seaLevel && water.data[it].isNaN()
    })
  }

  /** Chunks centred on a stride of conifer forest cells: the dry sweep crosses too little of it to count resin. */
  private val coniferChunks: Set<Pair<Int, Int>> by lazy {
    val biome: IntLayer = world.world.layers.require(LayerId.BIOME)
    val conifer = CONIFER_BIOMES.map { it.ordinal }.toSet()
    chunksAround(biome.data.indices.filter { biome.data[it] in conifer })
  }

  private fun chunksAround(cells: List<Int>): Set<Pair<Int, Int>> {
    val region = world.world.layers.require<FloatLayer>(LayerId.ELEVATION).region
    val metres = world.config.baseResolution.metresPerCell
    val stride = (cells.size / SAMPLES).coerceAtLeast(1)

    val chunks = LinkedHashSet<Pair<Int, Int>>()
    for (k in 0 until SAMPLES) {
      val index = cells.getOrNull(k * stride) ?: break
      val centreX = (region.minX + index % region.width + 0.5) * metres
      val centreY = (region.minY + index / region.width + 0.5) * metres
      val chunkX = world.config.chunkOf(centreX)
      val chunkY = world.config.chunkOf(centreY)
      for (dy in 0 until BLOCK) for (dx in 0 until BLOCK) chunks.add((chunkX + dx) to (chunkY + dy))
    }
    return chunks
  }

  private val items: List<Item> by lazy { itemsIn(sweptChunks) }

  private fun itemsIn(chunks: Set<Pair<Int, Int>>): List<Item> {
    val out = ArrayList<Item>()
    for ((chunkX, chunkY) in chunks) {
      val props = world.propsIn(chunkX, chunkY)
      for (i in props.indices) {
        if (props.kindAt(i) !in LootScatter.KINDS) continue
        out.add(Item(props.kindAt(i), props.identityAt(i), props.xAt(i), props.yAt(i), props.groundAt(i)))
      }
    }
    return out
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
  fun `a few lumps of resin lie under the conifers and nowhere else`() {
    val forest = itemsIn(coniferChunks)
    val hectares = coniferChunks.size * world.config.chunkExtent * world.config.chunkExtent / 10_000.0
    val resinRate = forest.count { it.kind == PropKind.RESIN } / hectares
    val stickRate = forest.count { it.kind == PropKind.STICK } / hectares
    println("over ${coniferChunks.size} conifer chunks: resin %.2f, sticks %.2f per hectare".format(resinRate, stickRate))
    // A few lumps, as the design promises: findable on a walk through the forest, but fewer than the sticks.
    assertTrue(resinRate in 0.3..10.0, "resin at %.2f per hectare of conifer forest".format(resinRate))
    assertTrue(resinRate < stickRate, "resin at %.2f is not rarer than sticks at %.2f".format(resinRate, stickRate))

    val surface = world.materializer.surface
    for (resin in (items + forest).filter { it.kind == PropKind.RESIN }) {
      val biome = surface.biomeAt(resin.x, resin.y)
      assertTrue(biome in CONIFER_BIOMES, "resin at (${resin.x}, ${resin.y}) lies in $biome")
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
    val CONIFER_BIOMES = setOf(Biome.TAIGA, Biome.TEMPERATE_RAINFOREST, Biome.TEMPERATE_FOREST)
  }
}
