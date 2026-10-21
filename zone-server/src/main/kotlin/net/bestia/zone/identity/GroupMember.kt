package net.bestia.zone.identity

import net.bestia.zone.util.AccountId

/** A member of a party or guild: which account owns which master. */
data class GroupMember(val accountId: AccountId, val masterId: Long)
