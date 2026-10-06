package net.bestia.zone.party

import net.bestia.zone.account.persistence.Party
import net.bestia.zone.identity.ecs.PartyMembership

/** The roster of this party, owner included, as a member's entity carries it. Read inside the loading transaction. */
fun Party.membership(): PartyMembership {
  return PartyMembership(id, (member.map { it.account.id } + owner.account.id).toSet())
}
