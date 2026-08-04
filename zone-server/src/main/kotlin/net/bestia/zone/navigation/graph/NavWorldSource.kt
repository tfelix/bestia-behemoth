package net.bestia.zone.navigation.graph

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.NavGraph
import net.bestia.zone.geometry.Vec3L

/**
 * What the macro graph needs from the world, and nothing else.
 *
 * The third seam of this shape, after `ecs/movement/GroundHeight` and `navigation/local/LocalWalkQuery`, and
 * for the same two reasons. It keeps `navigation/` from depending on `world/stream/` - the streaming layer
 * knows about chunk addresses, voxel sizes and the position-unit convention, and none of that belongs in a
 * pathfinder. And it makes the graph testable without a generated world: a test that cares whether a wolf
 * avoids a road should not have to build terrain to find out.
 *
 * **Only safe to call from the tick thread**, because the implementation is not thread safe.
 */
interface NavWorldSource {

  /** Whether the world exists yet. False during the boot window before the world-load runner has finished. */
  val isReady: Boolean

  /** The generated macro graph. Only called once, when the runtime graph is built. */
  fun navGraph(): NavGraph

  /**
   * Converts a world-space position in **metres** into ECS position units.
   *
   * The generator works in metres and the tick loop in position units; this is the one place that knows the
   * exchange rate.
   *
   * ### Horizontal only - the vertical is not this tier's business
   *
   * Implementations return zero for `z`, and every consumer of a macro position is written for that: route
   * planning compares nodes in two dimensions, and the moment a leg is turned into actual steps the local tier
   * asks the walkability tiles where the ground is. So a vertical here would be both unused and, since the
   * heightfield answers per chunk rather than per column, expensive enough to stall a tick to obtain.
   */
  fun place(metresX: Double, metresY: Double): Vec3L

  /** The canonical chunk holding a position, or null when it is off the world. */
  fun chunkAt(position: Vec3L): ChunkPos?

  /**
   * Registers a callback fired when a chunk's contents change.
   *
   * The handler runs inside the edit on the tick thread, so it must only mark something stale and return.
   */
  fun onChunkChanged(handler: (ChunkPos) -> Unit)
}
