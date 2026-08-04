package net.bestia.worldgen.voxel

/**
 * Whether one voxel may be removed at all, on grounds that come from the world rather than from the player.
 *
 * The one place to ask "may this be carved". Permission - a town, a quest structure, an instance boundary - is a
 * separate question that belongs to whatever owns those, and it is asked of a *region*; this is asked of a
 * voxel, and every answer here is a property of the material or of what is next to it.
 *
 * Both rules exist because there is no building system. A player who is refused cannot work around the refusal
 * by placing something, so a rule that merely made a mess would make a permanent one.
 */
object CarveRules {

  /**
   * Whether the voxel at [index] in [voxels] may be removed.
   *
   * @param voxels the **merged** chunk. Asking the base would let a player re-carve their way into a lake they
   *   had already opened a wall towards.
   */
  fun mayCarve(voxels: VoxelChunk, index: Int): Boolean {
    val material = BlockType.ofOrNull(voxels.blocks[index].toInt() and 0xFF) ?: return false

    return material.carvable && !wouldBreachFluid(voxels, index)
  }

  /**
   * Whether removing this voxel would leave a fluid with an open face into the hole.
   *
   * The wall between a gallery and a lake. **There is no runtime fluid state at all** - `LavaWells`,
   * `PondWater` and `RiverWater` are generation-time samplers over immutable vector features, and at runtime
   * water is a block id with the same standing as granite - so a breach would not flood. It would leave a dry
   * void under a lake, permanently, and with no building system the player could not seal it either.
   *
   * So the wall is simply not removable, which is a rule a player can read off the world: rock beside water
   * behaves like rock beside bedrock. That is a deliberate trade against a fluid simulation, which removal-only
   * makes *more* attractive rather than less - there is no counterplay to a flood - but which is a subsystem
   * with a tick budget and cross-chunk propagation, not a precondition.
   *
   * ### Six face neighbours, and only inside this chunk
   *
   * A diagonal neighbour shares no face, so nothing could flow through it even in a world that simulated flow.
   *
   * The chunk boundary is the real limitation, and it is a cost accepted rather than an oversight: checking
   * across it would mean holding the adjacent chunk on the path of every carved voxel, six times over, and the
   * consequence of missing those cases is a one-voxel-thick wall left standing at a chunk seam where the player
   * expected it to go. That is a strange-looking wall, not a hole in a lake - and it is the same trade
   * `ChunkBands` makes about boundaries it cannot see from one chunk's arrays.
   */
  fun wouldBreachFluid(voxels: VoxelChunk, index: Int): Boolean {
    val height = voxels.height
    val size = voxels.size

    val localZ = index % height
    val column = index / height
    val localX = column % size
    val localY = column / size

    // Strides for one step along each axis, given the vertical is contiguous. See VoxelChunk.columnOffset.
    val columnStride = height
    val rowStride = size * height

    if (localZ > 0 && isFluid(voxels, index - 1)) return true
    if (localZ < height - 1 && isFluid(voxels, index + 1)) return true
    if (localX > 0 && isFluid(voxels, index - columnStride)) return true
    if (localX < size - 1 && isFluid(voxels, index + columnStride)) return true
    if (localY > 0 && isFluid(voxels, index - rowStride)) return true
    if (localY < size - 1 && isFluid(voxels, index + rowStride)) return true

    return false
  }

  /**
   * The materials that would run into a hole if one were opened beside them.
   *
   * Ice is deliberately not one: it is solid, it is the *surface* of water rather than water, and a mined ice
   * sheet leaving a hole in itself is a hole in a solid, not a breached reservoir.
   */
  private fun isFluid(voxels: VoxelChunk, index: Int): Boolean {
    val block = BlockType.ofOrNull(voxels.blocks[index].toInt() and 0xFF) ?: return false

    return block == BlockType.WATER || block == BlockType.LAVA
  }
}
