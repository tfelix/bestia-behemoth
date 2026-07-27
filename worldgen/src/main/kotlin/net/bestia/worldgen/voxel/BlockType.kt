package net.bestia.worldgen.voxel

/**
 * The block palette.
 *
 * [id] is explicit and **permanent**: it goes into the RLE wire format, into chunk deltas, and into the
 * base hash the client compares against the server's. Ordinals would tie the format to the declaration
 * order of a Kotlin enum, which is exactly the kind of coupling that turns "insert a block type
 * alphabetically" into "every stored chunk in the world now decodes to the wrong rock".
 */
enum class BlockType(val id: Int, val solid: Boolean, val opaque: Boolean = solid) {

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

  // Ore, placed per voxel at chunk generation by sampling the sparse deposits. Never stored as a field.
  ORE_COPPER(50, solid = true),
  ORE_TIN(51, solid = true),
  ORE_IRON(52, solid = true),
  ORE_GOLD(53, solid = true),
  ORE_SILVER(54, solid = true),
  COAL_SEAM(55, solid = true),
  ROCK_SALT(56, solid = true),

  /** Bridge decking and other worked structure. */
  MASONRY(60, solid = true);

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
