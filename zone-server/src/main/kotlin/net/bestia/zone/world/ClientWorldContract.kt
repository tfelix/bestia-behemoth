package net.bestia.zone.world

import net.bestia.worldgen.core.WorldConfig

/**
 * The world geometry every client is built against, and the slope it draws as cliff.
 *
 * These used to ride on `WorldInfoSMSG`. They no longer do: a client cannot adapt to any of them at runtime,
 * so sending them bought nothing, and the client now compiles them in. What that costs is the possibility of
 * disagreement, which is what this object exists to detect - [disagreementsWith] is read by
 * `WorldService.load`, and a world that answers anything refuses to boot.
 *
 * Here rather than in `worldgen` because it spans two owners: the geometry is [WorldConfig]'s and the slope is
 * `ChunkStreamConfig`'s, and `zone-server` is the only side that holds both and talks to a client.
 *
 * **Must equal `WorldLayout.cs` in `bestia-client`.** The two move in the same commit;
 * `ClientWorldContractTest` fails the build naming that file when they drift.
 */
object ClientWorldContract {

  const val CHUNK_SIZE = 32
  const val CHUNK_HEIGHT = 256
  const val VOXEL_SIZE_METRES = 1.0
  const val CELL_SIZE_METRES = 1000.0
  const val WRAP_X = true
  const val WRAP_Y = true
  const val MAX_WALK_SLOPE_DEGREES = 60.0

  /**
   * Every way [config] and [maxWalkSlopeDegrees] would be rendered wrongly by a client built against this.
   *
   * All of them rather than the first, because a boot that names one field sends the operator round the loop
   * again for the next - and the whole reason this is a list of named comparisons rather than one digest is
   * that a digest cannot say which number moved.
   */
  fun disagreementsWith(config: WorldConfig, maxWalkSlopeDegrees: Double): List<String> {
    val found = mutableListOf<String>()

    compare(found, "chunk-size", CHUNK_SIZE, config.chunkSize)
    compare(found, "chunk-height", CHUNK_HEIGHT, config.chunkHeight)
    compare(found, "voxel-size-metres", VOXEL_SIZE_METRES, config.voxelSize)
    compare(found, "cell-size-metres", CELL_SIZE_METRES, config.baseResolution.metresPerCell)
    compare(found, "wrap-x", WRAP_X, config.wrapX)
    compare(found, "wrap-y", WRAP_Y, config.wrapY)
    compare(found, "max-walk-slope-degrees", MAX_WALK_SLOPE_DEGREES, maxWalkSlopeDegrees)

    return found
  }

  private fun compare(found: MutableList<String>, name: String, expected: Any, actual: Any) {
    if (expected != actual) {
      found.add("$name is $actual, clients are built for $expected")
    }
  }
}
