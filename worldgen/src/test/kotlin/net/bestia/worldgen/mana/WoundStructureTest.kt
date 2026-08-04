package net.bestia.worldgen.mana

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.PropKind
import net.bestia.worldgen.voxel.VoxelChunk
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A wound is a place, not a marker.
 *
 * `SiteKind.WOUND` can exist in the chronicle, get a `FeatureKind.WOUND` marker, be drawn on the viewer's
 * overlay, satisfy every invariant, and **still be a patch of ordinary grass a player walks over without
 * noticing** - which is precisely the shape `TODO.md` habit 6 records three times over. `SiteKind.BATTLEFIELD`
 * is the standing example inside this very file's subject: it has been in the chronicle from the start and
 * builds nothing at all.
 *
 * So this materialises the chunk at the centre of a wound and counts what came out of the ground.
 */
class WoundStructureTest {

  private val world: GeneratedWorld by lazy {
    StandardWorld.build(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  @Test
  fun `the centre of a wound materialises crystal spires`() {
    val wound = world.world.chronicle.sitesOfKind(SiteKind.WOUND).firstOrNull()
      ?: error("seed 7 has no wound; this test is pinned to a seed that does")

    val spires = spiresAt(wound.position)

    println("wound at (${wound.position.x.toInt()}, ${wound.position.y.toInt()}): $spires spires")

    // A 32 m chunk holds about 21 cells of the seven-metre spire lattice, and the density tapers to the field's
    // edge from 0.55 at its centre, so a chunk at the middle of a wound should hold something like a dozen.
    assertTrue(spires >= 6, "only $spires spires in the chunk at the centre of a wound")
  }

  @Test
  fun `the rampart stands above the ground it was thrown out of`() {
    // Its own test rather than a clause in the one above, because the crest and the crystal field are different
    // branches of `TownStructures.woundColumn` and a wound with no rim reads as a crystal garden.
    //
    // **Counting blighted dirt here proves nothing, and the first version of this test did exactly that.** The
    // ground inside a wound is fully corrupted, so `SurfaceCover.soil` fills the whole column under it with
    // BLIGHTED_DIRT anyway - 11 054 voxels of it in the crest chunk, none of them the rampart. What says a
    // rampart exists is that the solid surface is *higher than the heightfield says the ground is*, which is the
    // one thing the ordinary cover cannot produce.
    val wound = world.world.chronicle.sitesOfKind(SiteKind.WOUND).firstOrNull()
      ?: error("seed 7 has no wound")

    val crest = Vec2d(wound.position.x + wound.radius * 0.85, wound.position.y)
    val beyond = Vec2d(wound.position.x + wound.radius * 1.4, wound.position.y)

    val onCrest = riseAboveGround(crest)
    val outside = riseAboveGround(beyond)

    println("rise above the heightfield: %.2f m on the crest, %.2f m outside the wound".format(onCrest, outside))

    // 0.85r is exactly the crest of the hump - `sin(pi/2)` of the way across the band from 0.70r to the radius -
    // so this measures the full 3.4 m and comes back at 3.44. The bar is a metre rather than three so that
    // retuning the height is not a test change. Outside the radius nothing is added at all (measured: 0.08 m,
    // which is the voxel quantisation), and that is what makes this a rampart rather than a global offset
    // between the heightfield and the voxels.
    assertTrue(onCrest > 1.0, "the wound's rampart rises only %.2f m above the ground".format(onCrest))
    assertTrue(
      onCrest > outside + 1.0,
      "the crest stands %.2f m above ground and the land outside the wound stands %.2f m - the rampart is not " +
          "the thing being measured".format(onCrest, outside)
    )
  }

  @Test
  fun `ordinary corrupted ground has nothing like a spire field on it`() {
    // The control, and the specific thing it rules out: `CrystalScatter` also plants `MANA_CRYSTAL_LARGE`, so
    // "there are large crystals here" is no evidence at all that the wound built anything.
    //
    // **Nine control chunks rather than one.** A single chunk came back with zero, which made the comparison
    // `64 > 0` - true, and true of any implementation. The scatter's large share is about 95 crystals per square
    // kilometre on fully corrupted ground, which is a tenth of a crystal per 32 m chunk, so one chunk was always
    // going to measure nothing and prove nothing. Nine of them still expect well under one, and the assertion is
    // against their *total*, so the control now has to actually be quiet rather than merely be sampled thinly.
    val wound = world.world.chronicle.sitesOfKind(SiteKind.WOUND).firstOrNull()
      ?: error("seed 7 has no wound")

    val inside = spiresAt(wound.position)

    // The most corrupted dry cell that is not inside any wound: corrupted ground, so the scatter is at its full
    // density there, and the comparison is against the scatter rather than against empty land.
    val elsewhere = mostCorruptedCellAwayFromWounds()
    val extent = world.config.chunkExtent
    var outside = 0
    for (dy in -1..1) {
      for (dx in -1..1) {
        val at = Vec2d(elsewhere.x + dx * extent, elsewhere.y + dy * extent)
        outside += spiresAt(at)
      }
    }

    println(
      "spires: $inside in one wound chunk, $outside over nine chunks of corrupted ground away from any wound"
    )
    assertTrue(
      inside > outside * 3 && inside > 5,
      "$inside spires inside a wound against $outside over nine ordinary corrupted chunks - the wound " +
          "is not building anything the crystal scatter was not already doing"
    )
  }

  private fun countAt(position: Vec2d): Map<BlockType, Int> = count(materialiseAt(position))

  /** Metres of solid material standing above the heightfield's own answer for this column. */
  private fun riseAboveGround(position: Vec2d): Double {
    val config = world.config
    val chunk = materialiseAt(position)
    val extent = config.chunkExtent.toInt()
    val localX = Math.floorMod(position.x.toInt(), extent)
    val localY = Math.floorMod(position.y.toInt(), extent)

    val surface = chunk.solidHeightAt(localX, localY)
    if (surface < 0.0) return 0.0

    // Voxels above the chunk's own floor into metres, then compared with the heightfield. The floor comes from
    // the chunk's own `chunk.z` rather than from a recomputed one, so a chunk-indexing slip shows up as a
    // ridiculous number rather than cancelling out.
    val elevation = config.elevationOfVoxel(chunk.chunk.z * config.chunkHeight) + surface * config.voxelSize
    return elevation - world.base.heightAt(position.x, position.y)
  }

  private fun materialiseAt(position: Vec2d): VoxelChunk {
    val config = world.config
    val chunkX = Math.floorDiv(position.x.toInt(), config.chunkExtent.toInt())
    val chunkY = Math.floorDiv(position.y.toInt(), config.chunkExtent.toInt())
    val height = world.base.heightAt(position.x, position.y)
    return world.materializer.materialize(ChunkPos(chunkX, chunkY, config.chunkZOf(height)))
  }

  private fun count(chunk: VoxelChunk): Map<BlockType, Int> {
    val counts = HashMap<BlockType, Int>()
    for (block in chunk.blocks) {
      val type = BlockType.ofOrNull(block.toInt() and 0xFF) ?: continue
      if (type == BlockType.BLIGHTED_DIRT) counts.merge(type, 1, Int::plus)
    }
    return counts
  }

  /**
   * Props by kind in the chunk containing a position.
   *
   * **Spires, not voxels.** The old counts were voxels and had to say so loudly, because a spire is three to
   * nine of them and an assertion that reads as a spire count while measuring a voxel count is an order of
   * magnitude looser than it looks. A prop is one spire, so the numbers here mean what they say - and are
   * correspondingly smaller.
   */
  private fun spiresAt(position: Vec2d): Int {
    val config = world.config
    val chunkX = Math.floorDiv(position.x.toInt(), config.chunkExtent.toInt())
    val chunkY = Math.floorDiv(position.y.toInt(), config.chunkExtent.toInt())
    val props = world.propsIn(chunkX, chunkY)

    var spires = 0
    for (i in props.indices) if (props.kindAt(i) == PropKind.WOUND_SPIRE) spires++
    return spires
  }

  private fun mostCorruptedCellAwayFromWounds(): Vec2d {
    val corruption = world.world.layers.require<net.bestia.worldgen.core.FloatLayer>(
      net.bestia.worldgen.core.LayerId.CORRUPTION
    )
    val elevation = world.world.layers.require<net.bestia.worldgen.core.FloatLayer>(
      net.bestia.worldgen.core.LayerId.ELEVATION
    )
    val water = world.world.layers.require<net.bestia.worldgen.core.FloatLayer>(
      net.bestia.worldgen.core.LayerId.WATER_LEVEL
    )
    val wounds = world.world.chronicle.sitesOfKind(SiteKind.WOUND)
    val metres = corruption.region.resolution.metresPerCell
    val clear = 4_000.0

    var best = -1.0f
    var at: Vec2d? = null
    for (i in corruption.data.indices) {
      if (elevation.data[i] <= world.config.seaLevel) continue
      if (!water.data[i].isNaN()) continue
      if (corruption.data[i] <= best) continue

      val x = (corruption.region.minX + i % corruption.region.width + 0.5) * metres
      val y = (corruption.region.minY + i / corruption.region.width + 0.5) * metres
      if (wounds.any { it.position.distanceTo(Vec2d(x, y)) < clear }) continue

      best = corruption.data[i]
      at = Vec2d(x, y)
    }

    return at ?: error("no corrupted dry land away from a wound")
  }
}
