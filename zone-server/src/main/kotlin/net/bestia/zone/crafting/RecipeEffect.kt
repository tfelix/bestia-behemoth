package net.bestia.zone.crafting

/**
 * What a successful craft does.
 *
 * A [Recipe] with anything but [PRODUCE] is aimed at something the crafter picks. [ETCH_ESSENCE] aims at one of
 * the crafter's Bestias; the other three aim at an item instance the crafter is holding and refuse a plain
 * stackable pile: wear, upgrade level and rune slots are per-instance state, and a stack has none by construction.
 */
enum class RecipeEffect {
  /** Makes [Recipe.output] and puts it in the crafter's inventory. */
  PRODUCE,

  /**
   * Cuts one more rune slot into the target, up to the cap the crafter's Item Customization allows.
   * A failed cut may destroy the item outright - see [CraftBonusService.destroyChance].
   */
  ADD_SLOT,

  /** Raises the target's upgrade level by one, which every equip script scales off. */
  UPGRADE,

  /** Restores the target to full durability. Refuses an item that does not wear at all. */
  REPAIR,

  /**
   * Binds the target Bestia into the recipe's cut gem, which becomes an Essence Gem of its species. Only success
   * takes the Bestia: a failure costs the gem alone, like any other input.
   */
  ETCH_ESSENCE
}
