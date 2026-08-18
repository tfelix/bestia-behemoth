package net.bestia.zone.crafting

import net.bestia.zone.world.prop.StaticEntityKind

/**
 * One thing a crafter can do at a station, loaded from `recipes.yml`.
 *
 * Config rather than a row: nothing references a recipe by id from the database - a craft either
 * produced an item or modified an instance, and both of those are recorded as the item itself. So this
 * lives entirely in memory, the way `master_skill_tree.yml` and `prop-kinds.yml` do.
 *
 * [effect] is what makes one type cover both halves of the Craftsman tree. Producing an ingot and
 * cutting a rune slot into a sword differ only in what happens after the inputs are consumed and the
 * roll succeeds; everything before that - who may do it, where, at what cost, against what chance - is
 * identical, and four bespoke handlers would have had to agree on all of it four times.
 */
data class Recipe(
  val id: Long,
  val identifier: String,
  val effect: RecipeEffect,

  /** Set only for [RecipeEffect.PRODUCE]; the other effects change an item instead of making one. */
  val output: ItemStack?,

  /** Consumed whether the craft succeeds or fails. An empty list is allowed - Cooking needs no reagent. */
  val inputs: List<ItemStack>,

  val requiredSkillId: Long,
  val requiredSkillLevel: Int,

  /**
   * The station that must be standing within [net.bestia.zone.crafting.CraftingService.STATION_RANGE]
   * tiles, or null for something that needs none - a meal over any fire, a ritual on bare ground.
   */
  val station: StaticEntityKind?,

  /** Before [MasterCraftBonusService]'s time factor, which is the whole of what Master Craftsman buys. */
  val craftSeconds: Float,

  /** In `[0, 1]`, before skill bonuses. 1.0 is a craft that cannot fail on its own. */
  val baseSuccessChance: Float
) {

  init {
    require(craftSeconds > 0f) { "Recipe $identifier needs a positive craftSeconds, was $craftSeconds" }
    require(baseSuccessChance in 0f..1f) {
      "Recipe $identifier baseSuccessChance must be in [0, 1], was $baseSuccessChance"
    }
    require(requiredSkillLevel > 0) {
      "Recipe $identifier needs a positive requiredSkillLevel, was $requiredSkillLevel"
    }

    if (effect == RecipeEffect.PRODUCE) {
      requireNotNull(output) { "Recipe $identifier is PRODUCE and must declare an output" }
    } else {
      require(output == null) { "Recipe $identifier is $effect and must not declare an output" }
    }
  }

  /** Whether this recipe is aimed at an item the crafter already holds rather than at nothing. */
  val needsTarget: Boolean get() = effect != RecipeEffect.PRODUCE

  data class ItemStack(val itemId: Long, val amount: Int) {
    init {
      require(amount > 0) { "An item stack needs a positive amount, was $amount" }
    }
  }
}
