package net.bestia.worldgen.voxel

import net.bestia.worldgen.resource.OreGrade
import net.bestia.worldgen.resource.ResourceType

/**
 * Which block a resource looks like in the rock, and what a block of rock is made of.
 *
 * Both directions, in one table, because they have to stay each other's inverse. Generation needs
 * `(resource, grade) -> block`; whatever eventually lets a player break an ore voxel and pick up the metal
 * needs `block -> (resource, grade)`, and deriving the second from the ids by arithmetic would quietly tie
 * the palette layout to the mapping. The ids are explicit and permanent precisely so nothing does that.
 */
object OreBlocks {

  /** A resource and how rich the voxel is - what a broken ore block turns into. */
  data class Yield(val resource: ResourceType, val grade: OreGrade)

  private val GRADED: Map<ResourceType, Map<OreGrade, BlockType>> = mapOf(
    ResourceType.COPPER to triple(
      BlockType.ORE_COPPER_SMALL, BlockType.ORE_COPPER_MEDIUM, BlockType.ORE_COPPER_RICH
    ),
    ResourceType.TIN to triple(
      BlockType.ORE_TIN_SMALL, BlockType.ORE_TIN_MEDIUM, BlockType.ORE_TIN_RICH
    ),
    ResourceType.IRON to triple(
      BlockType.ORE_IRON_SMALL, BlockType.ORE_IRON_MEDIUM, BlockType.ORE_IRON_RICH
    ),
    // Both golds share a palette. A player panning a gravel bar and a player in a shaft are holding the same
    // metal, and the deposit marker already records which of the two it came out of.
    ResourceType.GOLD_LODE to triple(
      BlockType.ORE_GOLD_SMALL, BlockType.ORE_GOLD_MEDIUM, BlockType.ORE_GOLD_RICH
    ),
    ResourceType.GOLD_PLACER to triple(
      BlockType.ORE_GOLD_SMALL, BlockType.ORE_GOLD_MEDIUM, BlockType.ORE_GOLD_RICH
    ),
    ResourceType.SILVER to triple(
      BlockType.ORE_SILVER_SMALL, BlockType.ORE_SILVER_MEDIUM, BlockType.ORE_SILVER_RICH
    ),
    ResourceType.MITHRANDIUM to triple(
      BlockType.ORE_MITHRANDIUM_SMALL, BlockType.ORE_MITHRANDIUM_MEDIUM, BlockType.ORE_MITHRANDIUM_RICH
    ),
    ResourceType.SALT to triple(
      BlockType.ROCK_SALT_SMALL, BlockType.ROCK_SALT_MEDIUM, BlockType.ROCK_SALT_RICH
    ),
    ResourceType.SULFUR to triple(
      BlockType.ORE_SULFUR_SMALL, BlockType.ORE_SULFUR_MEDIUM, BlockType.ORE_SULFUR_RICH
    ),
    // The gems are in the graded map with the ores, because the vein machinery is what places graded bodies
    // and a vug field or a pegmatite is one - the grade is how much of the cavity is crystal rather than how
    // rich the rock is.
    ResourceType.PYRELITH to triple(
      BlockType.GEM_PYRELITH_SMALL, BlockType.GEM_PYRELITH_MEDIUM, BlockType.GEM_PYRELITH_RICH
    ),
    ResourceType.AMETHYST to triple(
      BlockType.GEM_AMETHYST_SMALL, BlockType.GEM_AMETHYST_MEDIUM, BlockType.GEM_AMETHYST_RICH
    ),
    ResourceType.EMERALD to triple(
      BlockType.GEM_EMERALD_SMALL, BlockType.GEM_EMERALD_MEDIUM, BlockType.GEM_EMERALD_RICH
    ),
    ResourceType.RUBY to triple(
      BlockType.GEM_RUBY_SMALL, BlockType.GEM_RUBY_MEDIUM, BlockType.GEM_RUBY_RICH
    ),
    ResourceType.DIAMOND to triple(
      BlockType.GEM_DIAMOND_SMALL, BlockType.GEM_DIAMOND_MEDIUM, BlockType.GEM_DIAMOND_RICH
    ),
    // In the map so `yieldOf` can name a broken block, but never reached through `blocksFor` from a deposit
    // marker - no marker ever carries this type. `OreVeins` looks it up directly when the ground around a
    // body is corrupted. See ResourceType.AETHERITE.
    ResourceType.AETHERITE to triple(
      BlockType.ORE_AETHERITE_SMALL, BlockType.ORE_AETHERITE_MEDIUM, BlockType.ORE_AETHERITE_RICH
    )
  )

  /**
   * Resources that show in the rock as a plain material rather than as gradeable ore.
   *
   * Marble and clay are quarried by the cubic metre, not picked up by the kilogram, so a grade would be a
   * number nobody could spend. They exist here so a marble deposit still *looks* like marble country when a
   * player digs into it.
   */
  private val PLAIN: Map<ResourceType, BlockType> = mapOf(
    ResourceType.MARBLE to BlockType.LIMESTONE,
    // Clay lost its own block when peat and clay became one MUD, and this is the right block for it anyway:
    // what a clay pit is dug out of is saturated fine sediment, which is what MUD now names.
    ResourceType.CLAY to BlockType.MUD,
    // Obsidian is here for marble's reason and one more: it is not disseminated through rock at all, so there is
    // nothing for a grade to be a grade *of*. A flow margin is either glass or it is not.
    ResourceType.OBSIDIAN to BlockType.OBSIDIAN
  )

  private val REVERSE: Map<BlockType, Yield> = buildMap {
    for ((resource, grades) in GRADED) {
      // Placer gold shares the lode's blocks, so the reverse map names the lode and the two agree about what
      // a gold block is worth. Putting the placer in would make the answer depend on map iteration order.
      if (resource == ResourceType.GOLD_PLACER) continue
      for ((grade, block) in grades) put(block, Yield(resource, grade))
    }
  }

  /** True for the graded ore blocks, which are the ones a pick turns into an item. */
  fun isOre(block: BlockType) = block in REVERSE

  /** The block one voxel of this resource at this grade looks like, or null if it is not in the rock. */
  fun blockFor(resource: ResourceType, grade: OreGrade): BlockType? = GRADED[resource]?.get(grade)

  /** All three grade blocks for a resource in [OreGrade] order, or null if it has none. */
  fun blocksFor(resource: ResourceType): List<BlockType>? =
    GRADED[resource]?.let { grades -> OreGrade.entries.map { grades.getValue(it) } }

  /** What this block yields when broken, or null if it is not ore. The inverse of [blockFor]. */
  fun yieldOf(block: BlockType): Yield? = REVERSE[block]

  /**
   * The plain material a non-gradeable resource shows as, or null if it does not show at all.
   *
   * Building stone, timber, furs and fish are the nulls: they are surface resources, and putting a block down
   * for them would be inventing geology to represent a forest.
   */
  fun plainBlockFor(resource: ResourceType): BlockType? = PLAIN[resource]

  private fun triple(small: BlockType, medium: BlockType, rich: BlockType) = mapOf(
    OreGrade.SMALL to small,
    OreGrade.MEDIUM to medium,
    OreGrade.RICH to rich
  )
}
