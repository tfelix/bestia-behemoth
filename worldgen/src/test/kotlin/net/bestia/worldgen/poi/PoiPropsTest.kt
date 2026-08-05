package net.bestia.worldgen.poi

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.PropId
import net.bestia.worldgen.voxel.PropInstances
import net.bestia.worldgen.voxel.PropKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The handover from a POI marker to a POI prop, on one world that has some.
 *
 * `PoiStageTest` asks whether the right landmarks exist; this asks whether the thing the world tier decided
 * actually reaches the chunk tier intact. The two halves are tested apart because they fail apart: a marker with
 * no prop is a clearance too narrow, and a prop with the wrong sub-kind or the wrong ground is a handover bug,
 * and a single test covering both would report either as the other.
 *
 * `Invariants.checkPoisBecomeProps` makes the same claims over a seed sweep, which is where a *rare* clearance
 * failure will show up. What is here that the sweep does not have is the exact-equality ground check and the
 * identity claims, which are properties of the emitter rather than of the tuning.
 *
 * The seed is pinned on holding at least one landmark, and the first assertion is that it does - a test that
 * silently checked nothing because the rolls all failed is the failure mode this whole subsystem's tests are
 * shaped around.
 */
class PoiPropsTest {

  @Test
  fun `the world under test actually holds a landmark`() {
    assertTrue(
      markers.isNotEmpty(),
      "seed $SEED at $CELLS cells holds no POI at all, so every other test in this class checks nothing. " +
          "Pick a seed that does."
    )
  }

  @Test
  fun `every marker becomes exactly one prop of the landmark it names`() {
    for (marker in markers) {
      val declared = PoiKind.entries[marker.attribute(PoiChannels.KIND).toInt()]
      val props = propsAround(marker)

      val matching = props.indices.filter {
        props.kindAt(it) == PropKind.POI &&
            props.xAt(it) == marker.position.x &&
            props.yAt(it) == marker.position.y
      }

      assertEquals(1, matching.size, "the ${declared.label} at ${marker.position} became ${matching.size} props")
      assertEquals(
        declared.ordinal,
        props.subKindAt(matching.single()),
        "the ${declared.label} at ${marker.position} emitted a prop naming a different landmark"
      )
    }
  }

  @Test
  fun `a landmark stands at its own column height, exactly`() {
    for (marker in markers) {
      val chunk = chunkOf(marker)
      val heights = world.columns.heights(chunk, 0)
      val props = world.materializer.propsIn(chunk.x, chunk.y, heights)
      val at = indexOf(props, marker)

      val voxelX = Math.floor(marker.position.x / world.config.voxelSize).toLong()
      val voxelY = Math.floor(marker.position.y / world.config.voxelSize).toLong()
      val localX = (voxelX - chunk.x.toLong() * world.config.chunkSize).toInt()
      val localY = (voxelY - chunk.y.toLong() * world.config.chunkSize).toInt()

      // No tolerance, for `Invariants.checkPropsAreWellPlaced`'s reason: the only way to be slightly wrong here
      // is to have read the base heightfield instead of the stamped column heights, and a tolerance would hide
      // exactly that.
      assertEquals(
        heights[localX, localY],
        props.groundAt(at),
        "a landmark at ${marker.position} stands at ${props.groundAt(at)} where its column reads " +
            "${heights[localX, localY]}"
      )
    }
  }

  @Test
  fun `a landmark carries its catalogue height and no spread`() {
    for (marker in markers) {
      val declared = PoiKind.entries[marker.attribute(PoiChannels.KIND).toInt()]
      val props = propsAround(marker)
      val at = indexOf(props, marker)

      // Through a float and back, because `PropInstances` stores heights as `Float` - eight parallel arrays over
      // millions of props is where that pays. Asserted as the exact round-trip rather than with a tolerance, so
      // this still fails if the height is the wrong entry's rather than merely close.
      assertEquals(
        declared.heightM.toFloat().toDouble(),
        props.heightAt(at),
        "the ${declared.label} is not its catalogue height"
      )
      assertEquals(0.0, props.radiusAt(at), "a landmark is a point; only a tree has a crown")
      assertTrue(!props.isBlighted(at), "the ${declared.label} carries a field sample it should not have")
      assertTrue(!props.isLarge(at), "the ${declared.label} carries a size variant it does not have")
    }
  }

  @Test
  fun `a landmark's name is its own and says what it is`() {
    for (marker in markers) {
      val props = propsAround(marker)
      val at = indexOf(props, marker)
      val name = props.identityAt(at)

      assertEquals(
        PropKind.POI,
        PropId.kindOf(name),
        "a landmark's packed identity does not decode to a POI"
      )

      // Against every prop in the chunk, trees included: a POI shares the chunk with hundreds of them and the
      // metre lattice it is named on is finer than any of theirs, so a collision would be a packing bug rather
      // than bad luck.
      val clashes = props.indices.count { props.identityAt(it) == name }
      assertEquals(1, clashes, "a landmark at ${marker.position} shares its name with another prop")
    }
  }

  private fun propsAround(marker: PointMarker) = chunkOf(marker).let { world.propsIn(it.x, it.y) }

  private fun indexOf(props: PropInstances, marker: PointMarker): Int =
    props.indices.single {
      props.kindAt(it) == PropKind.POI &&
          props.xAt(it) == marker.position.x &&
          props.yAt(it) == marker.position.y
    }

  private fun chunkOf(marker: PointMarker): ChunkPos {
    val size = world.config.chunkSize.toLong()
    return ChunkPos(
      Math.floorDiv(Math.floor(marker.position.x / world.config.voxelSize).toLong(), size).toInt(),
      Math.floorDiv(Math.floor(marker.position.y / world.config.voxelSize).toLong(), size).toInt(),
      0
    )
  }

  private companion object {

    /** Pinned on holding landmarks; `the world under test actually holds a landmark` is what enforces that. */
    const val SEED = 1L

    /** `PoiStageTest`'s size, so the two files are measuring the same worlds. */
    const val CELLS = 192

    val world = StandardWorld.build(
      StandardWorld.demoConfig(SEED).copy(widthCells = CELLS, heightCells = CELLS)
    )

    val markers: List<PointMarker> = world.world.features.all()
      .filter { it.kind == FeatureKind.POI }
      .filterIsInstance<PointMarker>()
  }
}
