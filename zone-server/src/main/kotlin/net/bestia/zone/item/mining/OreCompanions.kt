package net.bestia.zone.item.mining

import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.OreBlocks
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.skill.OwnerSkills
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.SkillIds
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The rare lump that comes out of the rock beside the ore it was mined for, and the grains of sand in plain stone.
 *
 * Mercury, palladium, adamantium and most of the gems have no deposit of their own in the world: giving each one
 * graded blocks, a geology and a place in the dispersal order is a world-generation change of its own. Obsidian has a
 * block but no item, and lodestone is the magnetite in iron. Until then
 * they ride along with a host ore that a real geologist would find them in - platinum-group metals in copper,
 * cinnabar in silver veins, adamantium in the deep mithrandium, sapphire in a ruby body - so every material in the
 * crafting chains can be mined by somebody. Retune the table, or replace a row with a deposit, without touching
 * anything else.
 *
 * Two rules keep it honest:
 *
 * - **A companion pays only when the miner could have mined it directly.** Its level is held against their Mining
 *   rank the way `OreInBrush` holds a host's, so a novice breaking copper never walks away with palladium.
 * - **Chance is a hash of the voxel, not a roll.** The same voxel always answers the same, so breaking, refilling
 *   and breaking again gives nothing new, and the world's ore stays a finite thing. Gold is left out entirely:
 *   what a gold voxel yields sizes the coin supply.
 */
@Component
class OreCompanions(
  private val items: ItemTemplateRegistry,
  private val skillIds: SkillIds,
) {

  /** What [host] may also give: an item identifier and its chance in thousandths per broken voxel. */
  data class Rider(val identifier: String, val permille: Int)

  /** An extra stack for one broken voxel. */
  class Extra(val itemId: Long, val amount: Int)

  /**
   * Extra stacks for one fully removed voxel of [block] at ([voxelX], [voxelY], [voxelZ]), mined by [minerId].
   * Empty for a block that is neither ore nor stone, for gold, and for anything above the miner's Mining rank.
   */
  fun extrasFor(world: World, minerId: EntityId, block: BlockType, voxelX: Long, voxelY: Long, voxelZ: Long): List<Extra> {
    val rock = block in ROCK
    val glass = block == BlockType.OBSIDIAN
    val host = OreBlocks.yieldOf(block)?.resource
    if (!rock && !glass && host == null) return emptyList()

    val maxLevel = MiningSkill.maxResourceLevelAt(miningLevelOf(world, minerId))
    val candidates = when {
      rock -> listOf(SAND)
      glass -> listOf(OBSIDIAN)
      else -> COMPANIONS[host].orEmpty()
    }

    return candidates.mapIndexedNotNull { index, companion ->
      val itemId = items.idOf(companion.identifier) ?: return@mapIndexedNotNull null
      val level = items.baseLevelOf(itemId) ?: 0
      if (maxLevel != null && level > maxLevel) return@mapIndexedNotNull null
      if (rollOf(voxelX, voxelY, voxelZ, companion.identifier, index) >= companion.permille) return@mapIndexedNotNull null

      Extra(itemId, 1)
    }
  }

  private fun miningLevelOf(world: World, minerId: EntityId): Int {
    val miningId = skillIds[SkillId.M_MINING] ?: return 0

    return OwnerSkills.levelOf(world, minerId, miningId)
  }

  companion object {

    /**
     * Sand in plain rock, "in low quantity" as designed: one voxel in two hundred, which is a pinch or two from a swing.
     * Any more and it fills a miner's pack before the ore does, and the ore is dropped at their feet.
     */
    val SAND = Rider("sand", 5)

    /** A cooled flow margin is glass all the way through: every voxel of it is a piece of obsidian. */
    val OBSIDIAN = Rider("obsidian", 1000)

    /** The stone a pick breaks that is not ore. */
    val ROCK: Set<BlockType> = setOf(BlockType.STONE, BlockType.GRANITE, BlockType.BASALT, BlockType.LIMESTONE)

    /**
     * Host ore to what it may also hold. The numbers are a first guess to be tuned by feel.
     *
     * Each companion is in the item catalogue and in `recipes.yml`'s chains; `CraftingChainTest` fails a raw material
     * that nothing here, in a drop table or in a prop hands out.
     */
    val COMPANIONS: Map<ResourceType, List<Rider>> = mapOf(
      ResourceType.COPPER to listOf(Rider("palladium_ore", 50)),
      ResourceType.SILVER to listOf(Rider("mercury_ore", 60)),
      ResourceType.MITHRANDIUM to listOf(Rider("adamantium_ore", 100)),
      ResourceType.RUBY to listOf(Rider("rough_sapphire", 300)),
      ResourceType.IRON to listOf(Rider("rough_garnet", 20), Rider("rough_tigers_eye", 10), Rider("lodestone", 30)),
      ResourceType.AMETHYST to listOf(Rider("rough_onyx", 60), Rider("rough_opal", 40)),
      ResourceType.EMERALD to listOf(Rider("rough_jade", 80)),
    )

    /** Every item identifier this hands out, for a test that asks whether a material can be obtained at all. */
    fun identifiers(): Set<String> = COMPANIONS.values.flatten().map { it.identifier }.toSet() + SAND.identifier + OBSIDIAN.identifier

    /** A thousandths roll in `[0, 1000)` that depends only on where the voxel is and which companion is asked. */
    internal fun rollOf(x: Long, y: Long, z: Long, identifier: String, index: Int): Int {
      var h = x * -7046029254386353131L xor y * -4658895280553007687L xor z * -2860486313293310349L
      h = h xor identifier.hashCode().toLong() * 0x9E3779B97F4A7C15uL.toLong() xor index.toLong()
      h = (h xor (h ushr 30)) * -4658895280553007687L
      h = (h xor (h ushr 27)) * -7723592293110705685L
      h = h xor (h ushr 31)

      return ((h ushr 1) % 1000L).toInt()
    }
  }
}
