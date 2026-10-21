package net.bestia.zone.party

import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.Party
import net.bestia.zone.identity.ecs.PartyMembership

/** The roster of this party, owner included, as a member's entity carries it. Read inside the loading transaction. */
fun Party.membership(): PartyMembership {
  return PartyMembership(id, everyone.map { it.asMember() }.toSet(), expShare)
}

fun Master.asMember(): PartyMembership.Member = PartyMembership.Member(accountId = account.id, masterId = id)
