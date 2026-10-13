package net.bestia.zone.ground

/**
 * How one layer's cells are laid out on the wire.
 *
 * [wireId] is `ChunkGroundLayerEncoding`'s number, by number rather than by name for `GroundLayer.wireId`'s
 * reason: a rename on either side must be a compile error or nothing, never a different layout.
 */
enum class GroundLayerEncoding(val wireId: Int) {

  /** `chunkSize²` nibbles, for a layer that is either there or not. */
  NIBBLE_V1(wireId = 1),

  /** Every non-zero cell at its exact level, for a layer the client keeps adding to itself. */
  LEVELS_SPARSE_V1(wireId = 2),
}
