package net.bestia.zone.crafting

import net.bestia.bnet.proto.CraftItemCmsgProto
import net.bestia.zone.message.CMSG

/**
 * The player picked a recipe in the crafting window and wants it made.
 *
 * Separate from `ActivateSkillCMSG` because that message carries no recipe: a skill that unlocks nine recipes
 * cannot say which one is wanted, so activation opens the window (see [CraftableRecipesSMSG]) and this executes
 * one line of it.
 */
data class CraftItemCMSG(
  override val playerId: Long,
  val recipeId: Long,

  /** The item instance to work on, or 0 for a recipe that makes something instead. */
  val targetUniqueId: Long
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, craftItem: CraftItemCmsgProto.CraftItemCMSG): CraftItemCMSG =
      CraftItemCMSG(
        playerId = accountId,
        recipeId = craftItem.recipeId,
        targetUniqueId = craftItem.targetUniqueId
      )
  }
}
