package net.bestia.worldgen.mana

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.OreBlocks
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Aetherite exists in the ground.
 *
 * **The likeliest of everything on this branch to ship dead**, and it would ship dead silently: roughly a
 * tenth of the land is corrupted, ore deposits are sparse and separated by twelve kilometres, and on a small
 * world the two sets can simply fail to intersect. Every unit test would pass, the palette would hold the
 * three block ids, `OreBlocks.yieldOf` would name them, and not one aetherite voxel would exist anywhere.
 *
 * So this is pinned to a seed that **has** a corrupted deposit, in the shape `SpecialSitesTest` uses, rather
 * than being a conditional check that passes vacuously on a world with none. If the pin ever fails because
 * that seed lost its deposit, find another seed - do not turn it into an `if`.
 */
class AetheriteTest {

  private fun world(seed: Long) =
    StandardWorld.build(WorldConfig(seed = seed, widthCells = 128, heightCells = 128))

  @Test
  fun `some world puts an ore body in corrupted ground`() {
    // The existence claim, over a handful of seeds. This is the count that says the subsystem is reachable
    // at all; the seed-pinned test below is what says it materialises.
    val worlds = (1L..8L).map { world(it) }
    val corruptedDeposits = worlds.sumOf { corruptedDepositCount(it) }

    assertTrue(
      corruptedDeposits > 0,
      "no ore body on any of eight worlds sits in corrupted ground - aetherite is unreachable"
    )
  }

  @Test
  fun `a corrupted deposit materialises as aetherite, entirely`() {
    val (world, marker) = firstCorruptedDeposit()
      ?: fail("no seed in 1..8 has a corrupted deposit; re-pin this test on one that does")

    val config = world.config
    val depth = marker.attribute(DepositChannels.DEPTH)
    val centreElevation = world.base.heightAt(marker.position.x, marker.position.y) - depth

    val chunk = world.materializer.materialize(
      ChunkPos(
        Math.floorDiv(marker.position.x.toInt(), config.chunkExtent.toInt()),
        Math.floorDiv(marker.position.y.toInt(), config.chunkExtent.toInt()),
        config.chunkZOf(centreElevation)
      )
    )

    var aetherite = 0
    var otherOre = 0
    for (block in chunk.blocks) {
      val type = BlockType.ofOrNull(block.toInt() and 0xFF) ?: continue
      val yielded = OreBlocks.yieldOf(type) ?: continue
      if (yielded.resource == ResourceType.AETHERITE) aetherite++ else otherOre++
    }

    assertTrue(aetherite > 0, "the chunk over a corrupted deposit holds no aetherite")

    // Entirely, not partly. The choice is made once per body in `OreVeins`'s constructor precisely so a body
    // cannot come out half metal and half aetherite; a mix here would mean it had drifted to per voxel.
    assertTrue(
      otherOre == 0,
      "$otherOre non-aetherite ore voxels share a chunk with $aetherite aetherite ones"
    )
  }

  @Test
  fun `an uncorrupted deposit is untouched`() {
    // The control. Without it, a materialiser that turned *every* ore into aetherite would pass both of the
    // assertions above and look entirely correct in a screenshot.
    val world = world(1L)
    val clean = world.world.features.all()
      .asSequence()
      .filter { it.kind == FeatureKind.ORE_DEPOSIT }
      .filterIsInstance<PointMarker>()
      .firstOrNull { corruptionAt(world, it) <= 0.0 }
      ?: fail("every deposit on this world is corrupted, which is not a world this pipeline makes")

    val config = world.config
    val depth = clean.attribute(DepositChannels.DEPTH)
    val centreElevation = world.base.heightAt(clean.position.x, clean.position.y) - depth

    val chunk = world.materializer.materialize(
      ChunkPos(
        Math.floorDiv(clean.position.x.toInt(), config.chunkExtent.toInt()),
        Math.floorDiv(clean.position.y.toInt(), config.chunkExtent.toInt()),
        config.chunkZOf(centreElevation)
      )
    )

    val aetherite = chunk.blocks.count {
      val type = BlockType.ofOrNull(it.toInt() and 0xFF)
      type != null && OreBlocks.yieldOf(type)?.resource == ResourceType.AETHERITE
    }

    assertTrue(aetherite == 0, "$aetherite aetherite voxels around a deposit in clean ground")
  }

  private fun firstCorruptedDeposit(): Pair<GeneratedWorld, PointMarker>? {
    for (seed in 1L..8L) {
      val world = world(seed)
      val marker = world.world.features.all()
        .asSequence()
        .filter { it.kind == FeatureKind.ORE_DEPOSIT }
        .filterIsInstance<PointMarker>()
        .firstOrNull { corruptionAt(world, it) >= world.params.corruption.aetheriteCorruption }
      if (marker != null) return world to marker
    }
    return null
  }

  private fun corruptedDepositCount(world: GeneratedWorld): Int {
    val threshold = world.params.corruption.aetheriteCorruption
    return world.world.features.all()
      .asSequence()
      .filter { it.kind == FeatureKind.ORE_DEPOSIT }
      .filterIsInstance<PointMarker>()
      .count { corruptionAt(world, it) >= threshold }
  }

  private fun corruptionAt(world: GeneratedWorld, marker: PointMarker): Double {
    val corruption = world.world.layers.require<FloatLayer>(LayerId.CORRUPTION)
    return corruption.sampleBilinear(marker.position.x, marker.position.y)
  }
}
