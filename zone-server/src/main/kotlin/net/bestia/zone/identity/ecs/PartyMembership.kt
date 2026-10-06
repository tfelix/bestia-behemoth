package net.bestia.zone.identity.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.party.Party

/**
 * Marks a master entity as being part of a party, caching the current roster's account ids so
 * other components (e.g. [net.bestia.zone.battle.ecs.status.Health]/[net.bestia.zone.battle.ecs.status.Mana])
 * can resolve party-wide sync targets with a plain component read instead of a DB lookup from the
 * tick thread. Kept up to date by [net.bestia.zone.party.PartyService] on every join/leave/kick/disband, once it has committed, and
 * added by `MasterEntitySpawner` when a member logs in.
 */
data class PartyMembership(
  val partyId: Long,
  val memberAccountIds: Set<Long>
) : Component {

  companion object {
    /** The roster of [party], owner included. Read inside the transaction that loaded it. */
    fun of(party: Party): PartyMembership {
      return PartyMembership(party.id, (party.member.map { it.account.id } + party.owner.account.id).toSet())
    }
  }
}
