package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.AccountId

/**
 * Goods spilled from somebody's keeping: a mail sack whose seal was broken, a chest knocked down. Taking them is
 * stealing, at the honor cost of [itemLevel], for everyone but [ownerAccountId], who takes back their own. Not
 * stored, so after a restart they lie there as plain items.
 */
data class SpilledProperty(val itemLevel: Int, val ownerAccountId: AccountId? = null) : Component
