package net.bestia.zone.party

import net.bestia.zone.message.CMSG

/**
 * Player leaves their current party. If the requester is the owner the whole party is disbanded
 * instead - there is no ownership transfer.
 */
data class LeavePartyCMSG(
  override val playerId: Long
) : CMSG
