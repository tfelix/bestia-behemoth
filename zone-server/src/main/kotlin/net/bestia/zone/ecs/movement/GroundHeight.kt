package net.bestia.zone.ecs.movement

/**
 * Where the ground is, so movement can put an entity on it instead of trusting where it was told to go.
 *
 * ### Why this is an interface and not just `ChunkService`
 *
 * The answer lives in `world.stream.ChunkService`, which is single-threaded on `zone-tick` and owns the chunk
 * store, the derived structures and the encoded-payload caches. `MoveSystem` needs one number out of all that,
 * and it runs on the same thread, so the coupling is safe - but depending on the whole service would point
 * `ecs/` at `world/stream/`, which nothing in `ecs/` does today, for the sake of one long.
 *
 * So this is the seam, in the same spirit as worldgen's own `BaseHeightField` and `ChunkColumnSource`: the
 * consumer states the question, and the layer that can answer it registers an adapter.
 *
 * **Only safe to call from the tick thread**, because the implementation is not thread safe. That is a property
 * of the implementation rather than of the contract, which is why it is said here as well as there.
 */
fun interface GroundHeight {

  /**
   * The `z` an entity standing at ([x], [y]) should have, in position units, or `null` where there is no answer.
   *
   * Null means the column is genuinely unavailable - off the grid, or the world is not generated yet - and not
   * "the ground is at zero". A caller that cannot tell those apart puts players at sea level in the middle of a
   * mountain range, so the distinction is worth a nullable return.
   */
  fun standingZAt(x: Long, y: Long): Long?
}
