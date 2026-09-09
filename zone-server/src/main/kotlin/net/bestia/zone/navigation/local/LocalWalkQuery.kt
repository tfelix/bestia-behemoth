package net.bestia.zone.navigation.local

import net.bestia.zone.geometry.Vec3L

/**
 * Whether an entity can actually get from one column to the next, over the ground as it is right now.
 *
 * ### Why this is an interface
 *
 * The same reason `ecs/movement/GroundHeight` is one, and the same shape. The answer lives in
 * `world.stream.ChunkService`, which owns the chunk store and the derived structures; the pathfinder needs
 * two booleans out of all that. Depending on the whole service would point `navigation/` at `world/stream/`
 * for the sake of a step test, so the consumer states the question here and the layer that can answer it
 * registers an adapter.
 *
 * ### This is where the walkable-slope rule is actually enforced
 *
 * Not in the macro graph, which sees a kilometre per cell and cannot resolve a cliff. The implementation
 * delegates to `DerivedStore.canStep`, which admits a step only when the rise is within
 * `AgentProfile.maxStep` - `tan` of `ChunkStreamConfig.maxWalkSlopeDegrees` per voxel of run, against the
 * real voxels including whatever players have built since. `WalkableSlopeTest` pins the conversion; the
 * angle itself is also what the client draws as cliff, which `ClientWorldContractTest` pins.
 *
 * The limit is symmetric: a drop is capped at the same rise, so a pit deeper than one step can be neither
 * entered nor left. That is deliberate - there is no fall anywhere in this server - and it is why a hole has
 * to be dug as a ramp to be walked into.
 *
 * **Only safe to call from the tick thread**, because the implementation is not thread safe.
 */
interface LocalWalkQuery {

  /**
   * Whether a single step between two adjacent columns is possible.
   *
   * Vertical is resolved by the implementation from [from]'s own column, so a caller may pass any `z` - what
   * matters is where the ground is, not where the caller thinks it is.
   */
  fun canStep(from: Vec3L, to: Vec3L): Boolean

  /** The `z` an entity standing at this column would have, or null when the column has no answer. */
  fun surfaceAt(position: Vec3L): Long?

  /**
   * Whether this column's data is loaded and cheap to ask about.
   *
   * The budget check that keeps a search off the expensive path: asking about an unloaded chunk forces a
   * materialisation - half a megabyte decoded - and a search that wandered into unloaded country would pay
   * that per column. A search treats "not resident" as "not walkable" rather than paying to find out.
   */
  fun isResident(position: Vec3L): Boolean
}
