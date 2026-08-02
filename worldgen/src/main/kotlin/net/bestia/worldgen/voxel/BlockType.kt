package net.bestia.worldgen.voxel

/**
 * The block palette.
 *
 * [id] is explicit and **permanent**: it goes into the RLE wire format, into chunk deltas, and into the
 * base hash the client compares against the server's. Ordinals would tie the format to the declaration
 * order of a Kotlin enum, which is exactly the kind of coupling that turns "insert a block type
 * alphabetically" into "every stored chunk in the world now decodes to the wrong rock".
 */
enum class BlockType(
  val id: Int,
  val solid: Boolean,
  val opaque: Boolean = solid,

  /**
   * How much of a sight line one full voxel of this material stops, in `[0,1]`.
   *
   * [opaque] is the boolean this refines, and foliage is why it had to be refined. Neither value the boolean
   * can take is right for a leaf canopy: `opaque = true` means one voxel of leaves in four stops a sight line
   * outright, so **no archer can shoot through any forest**, while `opaque = false` means a hundred metres of
   * canopy blocks nothing at all. A fraction says the true thing - a leaf voxel attenuates - and
   * `OpacityGrid` was already accumulating occupancy along a ray, so it needed only to weight by this
   * instead of branching.
   *
   * Defaults from [opaque], so every existing material keeps exactly the behaviour it had.
   */
  val opacity: Double = if (opaque) 1.0 else 0.0
) {

  AIR(0, solid = false, opaque = false),
  WATER(1, solid = false, opaque = false),
  ICE(2, solid = true),

  // Basement.
  GRANITE(10, solid = true),
  BASALT(11, solid = true),

  // Sedimentary cover.
  LIMESTONE(20, solid = true),
  SANDSTONE(21, solid = true),
  SHALE(22, solid = true),
  CONGLOMERATE(23, solid = true),

  // Unconsolidated.
  GRAVEL(30, solid = true),
  SAND(31, solid = true),
  CLAY(32, solid = true),
  DIRT(33, solid = true),
  PEAT(34, solid = true),
  PERMAFROST(35, solid = true),

  // Surface cover.
  GRASS(40, solid = true),
  SNOW(41, solid = true),

  /**
   * A tree trunk, scattered per column at chunk generation from a lattice hash. Never stored as a field.
   *
   * Solid, so it is an obstruction to path around and the ground a spawn point sits on is the ground rather
   * than the top of a trunk.
   */
  LOG(45, solid = true),

  /**
   * A tree canopy.
   *
   * **`solid = false` is doing three jobs at once**, and each of them would otherwise have been a change to a
   * derived structure. `VoxelChunk.highestSolid` reports the ground under a tree, so nothing spawns twelve
   * metres up in the branches; `WalkableTile` treats a non-solid block as passable, so agents walk *under* a
   * canopy instead of pathing across the treetops; and `highestNonAir` still counts it, so `probe` draws a
   * tree with no tooling change at all.
   *
   * [opacity] is the fourth, and is the one thing a boolean could not have expressed - see the parameter.
   */
  LEAVES(46, solid = false, opaque = false, opacity = 0.35),

  // Ore, placed per voxel at chunk generation by sampling the sparse deposits. Never stored as a field.
  ORE_COPPER(50, solid = true),
  ORE_TIN(51, solid = true),
  ORE_IRON(52, solid = true),
  ORE_GOLD(53, solid = true),
  ORE_SILVER(54, solid = true),
  COAL_SEAM(55, solid = true),
  ROCK_SALT(56, solid = true),

  /** Bridge decking and other worked structure. */
  MASONRY(60, solid = true),

  // Worked materials, for buildings and streets. Added with step 8; the palette version moves with them,
  // which is what the version gate exists to catch - a client one release behind cannot name these.
  TIMBER(61, solid = true),

  /** Wattle and daub, or lime render over timber. What most of a poor town is walled with. */
  PLASTER(62, solid = true),
  THATCH(63, solid = true),
  ROOF_TILE(64, solid = true),

  /** Floorboards and shutters. Solid but not load bearing, which nothing yet distinguishes. */
  /**
   * Sawn timber. **Nothing places it any more**, since the mine head stopped being a planked shaft cover and
   * became an open shaft; it is kept because building interiors want floors and a mine wants a headframe, both
   * of which are queued work. Delete it in the palette renumbering if neither has arrived by then, rather than
   * letting it become a material nobody can account for.
   */
  PLANK(65, solid = true),

  /** What a razed building leaves. Distinct from GRAVEL so a ruin reads as worked stone, not scree. */
  RUBBLE(66, solid = true),

  /** A paved street surface. */
  COBBLESTONE(67, solid = true);

  companion object {
    private val BY_ID = arrayOfNulls<BlockType>(entries.maxOf { it.id } + 1).also { table ->
      for (block in entries) {
        require(table[block.id] == null) { "Duplicate block id ${block.id}" }
        table[block.id] = block
      }
    }

    /** @throws IllegalArgumentException on an unknown id, which means a version mismatch, not a bug. */
    fun of(id: Int): BlockType = BY_ID.getOrNull(id)
      ?: throw IllegalArgumentException("Unknown block id $id; the chunk was written by another version")

    fun ofOrNull(id: Int): BlockType? = BY_ID.getOrNull(id)
  }
}
