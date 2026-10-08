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
   * @param waterFlows whether the server simulates water. Then the wall beside water may go, and the water runs
   *   into the hole.
   */
  fun mayCarve(voxels: VoxelChunk, index: Int, waterFlows: Boolean = false): Boolean {
    val material = BlockType.ofOrNull(voxels.blocks[index].toInt() and 0xFF) ?: return false

    return material.carvable && !wouldBreachFluid(voxels, index, waterFlows)
  }

  /**
   * Whether removing this voxel would leave a fluid with an open face into the hole that nothing would fill.
   *
   * The wall between a gallery and a lake. Lava never moves at runtime, so a breach beside it would leave a dry
   * void under the pool, permanently, and the player could not seal it either. The wall is simply not removable,
   * which is a rule a player can read off the world: rock beside lava behaves like rock beside bedrock. Water is
   * the same unless [waterFlows], when the simulation floods the hole instead.
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
  fun wouldBreachFluid(voxels: VoxelChunk, index: Int, waterFlows: Boolean = false): Boolean {
    val height = voxels.height
    val size = voxels.size

    val localZ = index % height
    val column = index / height
    val localX = column % size
    val localY = column / size

    // Strides for one step along each axis, given the vertical is contiguous. See VoxelChunk.columnOffset.
    val columnStride = height
    val rowStride = size * height

    if (localZ > 0 && isStillFluid(voxels, index - 1, waterFlows)) return true
    if (localZ < height - 1 && isStillFluid(voxels, index + 1, waterFlows)) return true
    if (localX > 0 && isStillFluid(voxels, index - columnStride, waterFlows)) return true
    if (localX < size - 1 && isStillFluid(voxels, index + columnStride, waterFlows)) return true
    if (localY > 0 && isStillFluid(voxels, index - rowStride, waterFlows)) return true
    if (localY < size - 1 && isStillFluid(voxels, index + rowStride, waterFlows)) return true

    return false
  }

  /**
   * The materials that would stand beside a hole opened next to them without running into it.
   *
   * Ice is deliberately not one: it is solid, it is the *surface* of water rather than water, and a mined ice
   * sheet leaving a hole in itself is a hole in a solid, not a breached reservoir.
   */
  private fun isStillFluid(voxels: VoxelChunk, index: Int, waterFlows: Boolean): Boolean {
    val block = BlockType.ofOrNull(voxels.blocks[index].toInt() and 0xFF) ?: return false

    return block == BlockType.LAVA || (block == BlockType.WATER && !waterFlows)
  }
}
