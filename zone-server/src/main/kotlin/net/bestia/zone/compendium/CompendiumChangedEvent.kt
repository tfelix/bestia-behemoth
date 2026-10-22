package net.bestia.zone.compendium

import net.bestia.zone.util.AccountId

/** [masterId]'s compendium gained a species or a catch, and is already stored. */
data class CompendiumChangedEvent(val accountId: AccountId, val masterId: Long)
