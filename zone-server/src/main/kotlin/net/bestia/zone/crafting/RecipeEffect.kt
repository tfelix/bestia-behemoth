package net.bestia.zone.crafting

/**
 * What a successful craft does. The four cover everything the Craftsman and Blacksmith trees can do.
 *
 * A [Recipe] with anything but [PRODUCE] is aimed at an item instance the crafter is holding, and every
 * one of those three refuses a plain stackable pile: wear, upgrade level and rune slots are per-instance
 * state, and a stack has none by construction.
 */
enum class RecipeEffect {
  /** Makes [Recipe.output] and puts it in the crafter's inventory. */
  PRODUCE,

  /**
   * Cuts one more rune slot into the target, up to the cap the crafter's Item Customization allows.
   * A failed cut may destroy the item outright - see [MasterCraftBonusService.destroyChance].
   */
  ADD_SLOT,

  /** Raises the target's upgrade level by one, which every equip script scales off. */
  UPGRADE,

  /** Restores the target to full durability. Refuses an item that does not wear at all. */
  REPAIR
}
