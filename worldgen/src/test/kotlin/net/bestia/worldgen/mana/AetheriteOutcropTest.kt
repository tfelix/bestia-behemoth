package net.bestia.worldgen.mana

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.AetheriteParams
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.PropKind
import net.bestia.worldgen.voxel.SurfaceCover
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The aetherite shards: that they exist, that they stand over corrupted ore, and that they are rare.
 *
 * ### Existing at all is the property worth testing hardest
 *
 * This is the third scatter in the module and the first whose presence depends on a **conjunction of two rare
 * things** - a corrupted province and an orebody inside it, shallow enough to reach daylight. That is exactly
 * the shape of the failure this module has shipped three times, and it very nearly shipped again here: the
 * first design ANDed corruption with thin soil and produced **zero to one site per world, mostly under an ice
 * sheet**. The survey that caught it is recorded on [AetheriteParams.maxDepth], and this test is what stops the
 * gate drifting back to a value that makes the whole feature dead.
 */
class AetheriteOutcropTest {

  private companion object {
    val world: GeneratedWorld = StandardWorld.build(
      StandardWorld.demoConfig(seed = 11753242L).copy(widthCells = 128, heightCells = 128)
    )

    val params = AetheriteParams()

    /** The corrupted, shallow deposits the scatter is allowed to build a field over. */
    val outcrops: List<PointMarker> by lazy {
      val corruption: FloatLayer = world.world.layers.require(LayerId.CORRUPTION)
      val threshold = CorruptionParams().aetheriteCorruption

      world.world.features.all()
        .filter { it.kind == FeatureKind.ORE_DEPOSIT }
        .filterIsInstance<PointMarker>()
        .filter { corruption.sampleBilinear(it.position.x, it.position.y) >= threshold }
        .filter { it.attribute(DepositChannels.DEPTH) <= params.maxDepth }
    }

    /**
     * Every shard in the chunks covering one outcrop's field.
     *
     * A field is at most a couple of hundred metres across against a chunk of `chunkSize` metres, so the nine
     * chunks around the body's own cover it whatever the alignment.
     */
    fun shardsAround(marker: PointMarker): List<Triple<Double, Double, Boolean>> {
      val size = world.config.chunkSize * world.config.voxelSize
      val cx = Math.floorDiv(marker.position.x.toLong(), size.toLong()).toInt()
      val cy = Math.floorDiv(marker.position.y.toLong(), size.toLong()).toInt()

      val out = ArrayList<Triple<Double, Double, Boolean>>()
      for (dy in -1..1) {
        for (dx in -1..1) {
          val props = world.materializer.propsIn(cx + dx, cy + dy)
          for (i in props.indices) {
            if (props.kindAt(i) != PropKind.AETHERITE_SHARD) continue
            out.add(Triple(props.xAt(i), props.yAt(i), props.isBlighted(i)))
          }
        }
      }
      return out
    }
  }

  @Test
  fun `the world has somewhere for a shard to stand`() {
    // Not an assertion about the scatter - an assertion about the *world*, and the one that says whether the
    // rest of this test is measuring anything. If this fails, the corruption stage or the ore dispersal moved
    // and `AetheriteParams.maxDepth` needs re-surveying rather than the scatter needing a fix.
    assertTrue(
      outcrops.isNotEmpty(),
      "no corrupted deposit within ${params.maxDepth} m of the surface on the reference world, so the " +
          "aetherite scatter has nothing to build on and every assertion below is vacuous"
    )
  }

  @Test
  fun `a shallow corrupted body carries a field of shards`() {
    // At least one body has to actually produce some. Stated over all of them rather than each, because a body
    // under an ice cap legitimately produces none - see the standsOn test below.
    val fields = outcrops.map { shardsAround(it).size }
    assertTrue(
      fields.any { it > 0 },
      "none of the ${outcrops.size} shallow corrupted bodies produced a single shard; fields were $fields"
    )
  }

  @Test
  fun `every shard stands inside some body's reach`() {
    // The claim the whole prop makes: a shard means *there is aetherite under this*. A shard outside every
    // body's field would be a hint pointing at nothing, which is worse than no hint.
    for (marker in outcrops) {
      val reach = marker.attribute(DepositChannels.RADIUS) * params.reachOfRadius

      for ((x, y, _) in shardsAround(marker)) {
        val nearest = outcrops.minOf { other ->
          val dx = x - other.position.x
          val dy = y - other.position.y
          sqrt(dx * dx + dy * dy) - other.attribute(DepositChannels.RADIUS) * params.reachOfRadius
        }
        assertTrue(
          nearest <= 0.0,
          "a shard at ($x, $y) is ${nearest.toInt()} m outside every body's field; the nearest reach is $reach m"
        )
      }
    }
  }

  @Test
  fun `every shard is blighted and stands on ground that is not ice`() {
    for (marker in outcrops) {
      for ((x, y, blighted) in shardsAround(marker)) {
        // Set by construction rather than sampled, because corrupted rock is what made the body yield
        // aetherite - a clean shard would be a contradiction, not a fringe case.
        assertTrue(blighted, "a shard at ($x, $y) is not blighted")

        val cap = SurfaceCover.cap(
          world.materializer.surface.biomeAt(x, y),
          world.materializer.surface.temperatureAt(x, y),
          0.0,
          blighted = true
        )
        assertTrue(
          cap != BlockType.ICE && cap != BlockType.SNOW,
          "a shard at ($x, $y) stands on $cap; the survey found corrupted bodies under ice on four of six seeds"
        )
      }
    }
  }

  @Test
  fun `shards are confined to the corrupted bodies and do not carpet the world`() {
    // The other half of rarity: a scatter that fired everywhere would satisfy every assertion above. Sampling
    // chunks well away from any outcrop is what distinguishes "over the ore" from "on corrupted ground", which
    // is the design the survey rejected.
    val size = world.config.chunkSize * world.config.voxelSize
    var sampled = 0
    var found = 0

    val chunksAcross = (world.config.widthMetres / size).toInt()
    var chunk = 0
    while (chunk < chunksAcross * chunksAcross && sampled < 60) {
      val cx = chunk % chunksAcross
      val cy = chunk / chunksAcross
      val centreX = (cx + 0.5) * size
      val centreY = (cy + 0.5) * size

      val nearOutcrop = outcrops.any {
        val dx = centreX - it.position.x
        val dy = centreY - it.position.y
        sqrt(dx * dx + dy * dy) < size * 2
      }
      // Coprime-ish stride so the sample is not one band of the map.
      chunk += 7
      if (nearOutcrop) continue

      sampled++
      val props = world.materializer.propsIn(cx, cy)
      for (i in props.indices) {
        if (props.kindAt(i) == PropKind.AETHERITE_SHARD) found++
      }
    }

    assertTrue(sampled > 0, "sampled no chunks away from an outcrop; the test measured nothing")
    assertTrue(
      found == 0,
      "$found shards in $sampled chunks that hold no shallow corrupted body; the field is not confined to the ore"
    )
  }
}
