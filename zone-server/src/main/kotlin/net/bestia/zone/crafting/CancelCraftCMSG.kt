package net.bestia.zone.crafting

import net.bestia.zone.message.CMSG

/**
 * The player abandoned the craft in progress.
 *
 * Carries nothing: an entity crafts at most one thing at a time and the server knows which. Nothing is
 * refunded because nothing has been spent - inputs are consumed when a craft resolves.
 */
data class CancelCraftCMSG(
  override val playerId: Long
) : CMSG
