package net.bestia.worldgen.voxel

/**
 * The block palette.
 *
 * [id] is explicit and **permanent**: it goes into the RLE wire format, into chunk deltas, and into the
 * base hash the client compares against the server's. Ordinals would tie the format to the declaration
 * order of a Kotlin enum, which is exactly the kind of coupling that turns "insert a block type
 * alphabetically" into "every stored chunk in the world now decodes to the wrong rock".
 *
 * ### The gaps stay
 *
 * The ids are sparse - the bands are basement 10-11, sedimentary 20-23, unconsolidated 30-35, surface cover
 * 40-46, worked 60-67, ore 100-120 - and the palette pass considered renumbering them densely to fit a wire
 * format that packs material into six bits. It was measured instead: dense ids saved **zero bytes**, because
 * the only ids past 64 are ore and ore is rare, and the format in question was declined on its own numbers.
 * See `RleCodec`'s table. So the bands are worth what they cost, which is nothing: `is this ore` is a range
 * check a human can do while reading the file, and a new worked material has somewhere obvious to go.
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

  /*
   * Mana crystal, scattered per column at chunk generation from a lattice hash exactly as a tree is, and
   * stored nowhere. Two sizes rather than three grades: a crystal is harvested off the ground rather than
   * dug out of a body, so there is no tonnage to reconcile and nothing for `OreBlocks` to name.
   *
   * Here rather than in the ore band because a crystal grows on the surface. Which band a material sits in
   * is the only documentation of what kind of thing it is, and putting a plant-like resource among the
   * lodes would make `isOre`'s range check a lie for a human reader.
   */
  MANA_CRYSTAL_SMALL(47, solid = true),
  MANA_CRYSTAL_LARGE(48, solid = true),

  /*
   * What the surface cover above becomes on corrupted ground.
   *
   * A twin per cover material rather than a flag on the block, because the wire format is a byte of block id
   * and nothing else - so a corrupted world costs the client six palette rows and no protocol change at all.
   * `voxel/SurfaceCover.blight` is the only thing that maps one to the other.
   *
   * Six, not the whole palette. Snow, ice, gravel and the rocks are deliberately not twinned: snow on cursed
   * ground is still snow, the bare biomes that use gravel and clay are rare, and blighting bedrock would
   * turn every mine shaft inside a province purple for no gameplay reason. Those places still read as
   * corrupted through the crystal scatter.
   */
  BLIGHTED_GRASS(49, solid = true),
  BLIGHTED_DIRT(50, solid = true),
  BLIGHTED_SAND(51, solid = true),
  BLIGHTED_PEAT(52, solid = true),
  BLIGHTED_LOG(53, solid = true),

  /** Flags copied from [LEAVES] exactly; a blighted canopy occludes the same as a live one. */
  BLIGHTED_LEAVES(54, solid = false, opaque = false, opacity = 0.35),

  /** Bridge decking and other worked structure. */
  MASONRY(60, solid = true),

  // Worked materials, for buildings and streets. Added with step 8; the palette version moves with them,
  // which is what the version gate exists to catch - a client one release behind cannot name these.
  TIMBER(61, solid = true),

  /** Wattle and daub, or lime render over timber. What most of a poor town is walled with. */
  PLASTER(62, solid = true),
  THATCH(63, solid = true),
  ROOF_TILE(64, solid = true),

  // 65 was PLANK, sawn timber for floorboards and shutters. Nothing had placed it since the mine head stopped
  // being a planked shaft cover and became an open shaft, and its own KDoc said to delete it at the palette
  // pass if neither building interiors nor a mine headframe had arrived. Neither had. The id is left free for
  // whichever of them lands first.

  /** What a razed building leaves. Distinct from GRAVEL so a ruin reads as worked stone, not scree. */
  RUBBLE(66, solid = true),

  /** A paved street surface. */
  COBBLESTONE(67, solid = true),

  /*
   * Ore, placed per voxel at chunk generation by sampling the sparse deposits. Never stored as a field.
   *
   * Three grades per ore, and the grade is the point: the block a player breaks has to say how much metal
   * falls out of it, and a single ORE_IRON cannot. `OreBlocks` owns the mapping in both directions -
   * (resource, grade) -> block for generation, block -> (resource, grade) for whatever eventually turns a
   * broken voxel into an item.
   *
   * Ids start at 100 rather than continuing from 67 because the ungraded ore band that used to sit at 50-56
   * was deleted with this change. A fresh contiguous band is worth the gap: it makes "is this ore" a range
   * check for a human reading the file, and it leaves 68-99 for the next batch of worked materials rather
   * than interleaving them with rock.
   */
  ORE_COPPER_SMALL(100, solid = true),
  ORE_COPPER_MEDIUM(101, solid = true),
  ORE_COPPER_RICH(102, solid = true),

  ORE_TIN_SMALL(103, solid = true),
  ORE_TIN_MEDIUM(104, solid = true),
  ORE_TIN_RICH(105, solid = true),

  ORE_IRON_SMALL(106, solid = true),
  ORE_IRON_MEDIUM(107, solid = true),
  ORE_IRON_RICH(108, solid = true),

  ORE_GOLD_SMALL(109, solid = true),
  ORE_GOLD_MEDIUM(110, solid = true),
  ORE_GOLD_RICH(111, solid = true),

  ORE_SILVER_SMALL(112, solid = true),
  ORE_SILVER_MEDIUM(113, solid = true),
  ORE_SILVER_RICH(114, solid = true),

  ORE_MITHRANDIUM_SMALL(115, solid = true),
  ORE_MITHRANDIUM_MEDIUM(116, solid = true),
  ORE_MITHRANDIUM_RICH(117, solid = true),

  ROCK_SALT_SMALL(118, solid = true),
  ROCK_SALT_MEDIUM(119, solid = true),
  ROCK_SALT_RICH(120, solid = true),

  /**
   * Ore that came out of corrupted ground.
   *
   * Not a separate deposit and not a separate `MinableOre`: the body is iron or copper like any other, and
   * the mana in the rock around it is what makes what you dig out aetherite. The choice is made once per
   * deposit in `ChunkStructures.OreVeins`, so a body is entirely one or entirely the other and no player
   * ever finds a seam that changes metal halfway along.
   *
   * Three grades like every other ore, because `OreBlocks` maps `(resource, grade)` in both directions and a
   * material that broke that symmetry would need its own case in the reverse map.
   */
  ORE_AETHERITE_SMALL(121, solid = true),
  ORE_AETHERITE_MEDIUM(122, solid = true),
  ORE_AETHERITE_RICH(123, solid = true);

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
