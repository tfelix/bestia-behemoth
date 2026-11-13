package net.bestia.zone.spellbinding

import net.bestia.zone.util.AccountId

/** Who made a binding: whom it reports to, whose things it spares, and the name Magic Sense reads at level 5. */
data class SpellBindingOwner(
  val accountId: AccountId,
  val masterId: Long,
  val masterName: String,
)
