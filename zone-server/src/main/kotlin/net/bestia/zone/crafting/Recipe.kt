package net.bestia.zone.crafting

import net.bestia.zone.entity.StaticEntityKind

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
   * The station that must be standing within [net.bestia.zone.prop.PlayerStructureService.RANGE_TILES]
   * tiles, or null for something that needs none - a meal over any fire, a ritual on bare ground.
   */
  val station: StaticEntityKind?,

  /** Before [CraftBonusService]'s time factor, which is the whole of what Master Craftsman buys. */
  val craftSeconds: Float,

  /**
   * At most 1, before skill bonuses. 1.0 is a craft that cannot fail on its own.
   *
   * **May be negative**, and the design docs rely on it: the base chance falls with the level of what is being
   * made (an ore smelts at `0.30 - max(0, level - 10) * 0.01`, a forged item at `0.90 - max(0, level - 10) * 0.015`),
   * so a high-level craft starts below zero and only skill, stats and bonuses lift it into reach.
   * [CraftBonusService.successChance] adds those and clamps the total, so the base never has to.
   */
  val baseSuccessChance: Float
) {

  init {
    require(craftSeconds > 0f) { "Recipe $identifier needs a positive craftSeconds, was $craftSeconds" }
    require(baseSuccessChance.isFinite() && baseSuccessChance <= 1f) {
      "Recipe $identifier baseSuccessChance must be at most 1, was $baseSuccessChance"
    }
    require(requiredSkillLevel > 0) {
      "Recipe $identifier needs a positive requiredSkillLevel, was $requiredSkillLevel"
    }

    if (effect == RecipeEffect.PRODUCE) {
      requireNotNull(output) { "Recipe $identifier is PRODUCE and must declare an output" }
    } else {
      require(output == null) { "Recipe $identifier is $effect and must not declare an output" }
    }

    if (effect == RecipeEffect.ETCH_ESSENCE) {
      require(inputs.singleOrNull()?.amount == 1) { "Recipe $identifier etches exactly one cut gem" }
    }
  }

  /** Whether this recipe is aimed at something the crafter picks rather than at nothing. */
  val needsTarget: Boolean get() = effect != RecipeEffect.PRODUCE

  /** Whether the target is one of the crafter's Bestias rather than a held item. */
  val etches: Boolean get() = effect == RecipeEffect.ETCH_ESSENCE

  /** The cut gem an etching binds the Bestia into. */
  val etchedGemId: Long get() = inputs.single().itemId

  /** Item id -> amount, summed per item, so a recipe naming a material twice is checked against the total. */
  val inputTotals: Map<Long, Int>
    get() {
      return inputs.groupBy({ it.itemId }, { it.amount }).mapValues { (_, amounts) -> amounts.sum() }
    }

  data class ItemStack(val itemId: Long, val amount: Int) {
    init {
      require(amount > 0) { "An item stack needs a positive amount, was $amount" }
    }
  }
}
