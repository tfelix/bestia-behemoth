package net.bestia.zone.world.mining

import net.bestia.worldgen.resource.OreGrade
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.OreBlocks
import net.bestia.zone.ecs.item.ItemTemplateRegistry
import org.springframework.stereotype.Service

/**
 * What a broken ore voxel puts in a pack.
 *
 * The caller `OreBlocks.yieldOf` was written for and never had. Only two resources have an item to come
 * out as; the rest of the palette is in the rock with nowhere to go, and is left there rather than given
 * a placeholder item nobody can use.
 */
@Service
class OreYield(private val items: ItemTemplateRegistry) {

  class Stack(val itemId: Long, val amount: Int)

  /** What a fully removed voxel of [block] yields, or null when it is not ore we can hand over. */
  fun of(block: BlockType): Stack? {
    val broken = OreBlocks.yieldOf(block) ?: return null
    val identifier = ITEM_BY_RESOURCE[broken.resource] ?: return null
    val itemId = items.idOf(identifier) ?: return null

    return Stack(itemId, amountFor(broken.grade))
  }

  companion object {

    /**
     * Lumps of ore a voxel yields, by grade.
     *
     * The grades come up 5:3:1, so 1, 2 and 7 average to **exactly two lumps a voxel** - one bar, and
     * therefore exactly the coin the money supply is quoted at per voxel. The kilograms behind the
     * grades are 0.5, 1 and 5, so a rich voxel pays a little less here than its mass says: ten would
     * make the average 2.33 and leave the mint owing a fraction of a coin on every voxel in the world.
     */
    fun amountFor(grade: OreGrade): Int {
      return when (grade) {
        OreGrade.SMALL -> 1
        OreGrade.MEDIUM -> 2
        OreGrade.RICH -> 7
      }
    }

    /**
     * Placer gold is absent because `OreBlocks` never reports it - placer and lode share the same three
     * blocks, and the reverse map keeps the lode so that a gold block has one answer rather than two.
     */
    private val ITEM_BY_RESOURCE = mapOf(
      ResourceType.IRON to "iron_ore",
      ResourceType.GOLD_LODE to "gold_ore",
    )
  }
}
