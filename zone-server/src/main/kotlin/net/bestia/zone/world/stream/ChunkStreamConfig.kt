package net.bestia.zone.world.stream

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

/**
 * Runtime settings for chunk streaming.
 *
 * Separate from `worldgen`, which holds a world's *birth* settings and is ignored once a world exists.
 * These can all change on a restart without consequence, because none of them affects what the terrain is.
 *
 * @property viewRadiusChunks horizontal radius in chunks around the player's own. Five gives an 11x11 square,
 *   352 m across and 176 m from centre to edge - sized against a draw distance of about 200 m. The cost is
 *   quadratic: seven would be 225 chunks rather than 121. A square rather than a disc on purpose; the frustum
 *   it is standing in for is rectangular.
 * @property viewRadiusChunksVertical vertical radius in *slabs*. A view volume wants bounding in z for the
 *   same reason it wants bounding in x and y, and a slab is 256 m tall, so one is already a 768 m column -
 *   far more than an isometric camera can show. This is a ceiling on what the surface rule may offer, not a
 *   box that is subscribed to outright: see [ChunkService.surfaceSlabsOf].
 * @property chunksPerTickPerPlayer how many chunk payloads one connection may be sent per tick. At the
 *   default tick rate of 20 this is 80 chunks a second, so an initial 121-chunk load spreads over about
 *   one and a half seconds. The ceiling exists for the *client's* sake as much as the socket's: it drains
 *   its whole receive queue in a single frame, so an unbudgeted burst is a visible stutter.
 * @property requestBurst token bucket depth for [ChunkRequestCMSG]. A client legitimately asks for a whole
 *   manifest at once on login, so the burst has to cover that; the refill is what limits sustained asking.
 * @property requestRefillPerTick tokens returned per tick, so the sustained ceiling is this times the tick
 *   rate - 40 chunk requests a second at the defaults.
 * @property deflateMinimumBytes payloads smaller than this are not compressed at all. Deflate has a fixed
 *   overhead and loses outright on the very smallest chunks: a uniform underground chunk is thirteen bytes
 *   encoded and nineteen deflated, and most of the world is that chunk.
 * @property encodedCacheCapacity how many encoded, compressed chunk payloads to keep. This is the entry
 *   that makes thirty players walking into the same place cost one encode between them rather than thirty,
 *   so it wants to comfortably exceed one view volume.
 */
@ConfigurationProperties(prefix = "chunk-stream")
@ConfigurationPropertiesScan
data class ChunkStreamConfig(
  val viewRadiusChunks: Int = 5,
  val viewRadiusChunksVertical: Int = 1,
  val chunksPerTickPerPlayer: Int = 4,
  val requestBurst: Int = 512,
  val requestRefillPerTick: Int = 2,
  val deflateMinimumBytes: Int = 64,
  val encodedCacheCapacity: Int = 1024,

  /**
   * Deflate level for chunk payloads, 0..9.
   *
   * The full setting despite this being a socket rather than a disk, because a payload is compressed once
   * per revision and then served to every player who asks - so the cost is amortised over every recipient
   * and every re-send, while the saving is paid on each of them.
   */
  val deflateLevel: Int = 9,

  /**
   * Decoded chunks held for the server's own voxel queries. Half a megabyte each, so this is the setting
   * that decides the memory footprint: 128 is about 64 MB. Distinct from [encodedCacheCapacity], which
   * counts three-kilobyte wire payloads.
   */
  val hotChunkCapacity: Int = 128,

  /**
   * Cached vertical slabs, one entry per horizontal chunk column.
   *
   * A two- or three-element array per entry, so this can be generous - and it needs to be. Computing one
   * means a feature query plus a thousand noise evaluations, and a manifest asks about a whole view volume
   * every time a player crosses a chunk boundary. 16 384 covers roughly a hundred and thirty view volumes, so
   * a handful of players moving around a region keep hitting it. Never invalidated, because what it holds is
   * the *generated* slabs and the heightfield those derive from is immutable - the slabs a player's digging
   * adds are tracked separately and unioned in on read, so an edit needs no invalidation here either. See
   * [ChunkService.surfaceSlabsOf].
   */
  val slabCacheCapacity: Int = 16_384,

  /**
   * New columns' slabs computed per tick, across all players.
   *
   * The cache above makes a *repeated* manifest free, but the first one over fresh ground still has to sample
   * the heightfield for every column in the view volume - 121 chunks at the default radius, each a feature
   * query plus a thousand noise evaluations. Doing that in one tick stalls the zone thread for long enough to
   * be measured in whole ticks, which showed up as unrelated scenario tests timing out waiting for ordinary
   * health regeneration to propagate.
   *
   * So a manifest grows instead. Columns whose slabs are not yet known are simply not offered this tick, and
   * the next manifest adds them - which the protocol already supports, since a manifest is a diff. Terrain
   * arrives over a second or so on genuinely new ground and instantly once anyone has been there.
   */
  val slabComputationsPerTick: Int = 4,

  /**
   * Derived structures built or rebuilt per tick, across all players. Queries keep serving the stale structure
   * until then, which is the trade the design argues for: pathing briefly wrong beats a rebuild hitch on the
   * zone thread every time somebody places a block.
   *
   * This budget now pays for two things rather than one. It always covered *rebuilds* after an edit, and it
   * also covers the **first** build of every chunk entering a subscription - see the `onFirstSubscriber` wiring
   * in `ChunkStreamSystem`, which is what gives `DerivedStore` any residency at all. A login subscribes a
   * whole view volume at once, so at the default tick rate of 20 this is 160 chunks a second and fresh ground
   * becomes walkable inside a second; at the old 2 it took three.
   *
   * One build is a `ChunkStore.merged` plus three full passes over a chunk's 262 144 voxels. Two of those
   * three - `ColumnSummary` and `OpacityGrid` - have no gameplay reader today; splitting them out so only
   * `WalkableTile` is built eagerly is the obvious next saving if this ever shows up on the tick budget.
   */
  val derivedRebuildsPerTick: Int = 8,

  /** Whether the `/carve` chat command is honoured at all. Off in production; the authority check applies too. */
  val allowDebugEdits: Boolean = true,

  /**
   * Steepest rise a step may cross and still count as walkable, in degrees.
   *
   * Feeds the one [net.bestia.worldgen.derived.AgentProfile] shared by NPC pathfinding and the player's own
   * move validation - see `AgentProfile.forMaxSlope` - so retuning this retunes both together. Forty-five is
   * not an arbitrary default: it is the angle at which a rise equals its run, which is why it was already the
   * hard-coded step height before this became a setting.
   */
  val maxWalkSlopeDegrees: Double = 45.0
) {

  init {
    require(viewRadiusChunks >= 0) { "View radius cannot be negative" }
    require(viewRadiusChunksVertical >= 0) { "Vertical view radius cannot be negative" }
    require(chunksPerTickPerPlayer > 0) { "A send budget of zero would never stream anything" }
    require(requestBurst > 0 && requestRefillPerTick > 0) { "The request bucket must be able to refill" }
    require(encodedCacheCapacity > 0) { "The encoded cache must hold at least one chunk" }
    require(hotChunkCapacity > 0) { "The hot chunk cache must hold at least one chunk" }
    require(slabCacheCapacity > 0) { "The slab cache must hold at least one column" }
    require(slabComputationsPerTick > 0) { "A slab budget of zero would never offer any terrain" }
    require(deflateLevel in 0..9) { "Deflate level must be 0..9, was $deflateLevel" }
    require(derivedRebuildsPerTick >= 0) { "Rebuild budget cannot be negative" }
    require(maxWalkSlopeDegrees > 0.0 && maxWalkSlopeDegrees < 90.0) {
      "maxWalkSlopeDegrees must be strictly between 0 and 90, was $maxWalkSlopeDegrees"
    }
  }

  /** Chunks along one edge of the horizontal view square, for sizing and for logging what a login will cost. */
  val chunksAcrossView get() = 2 * viewRadiusChunks + 1
}
