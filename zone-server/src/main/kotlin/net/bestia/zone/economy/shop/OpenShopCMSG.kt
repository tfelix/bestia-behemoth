package net.bestia.zone.economy.shop

import net.bestia.zone.message.CMSG

/**
 * A request for the prices of wherever the sender is standing.
 *
 * Carries no target, and that is the rule rather than an omission: a client only ever learns the prices
 * of the settlement it is in, because a world where every price is visible at once turns the merchant
 * profession into a spreadsheet.
 */
data class OpenShopCMSG(override val playerId: Long) : CMSG
