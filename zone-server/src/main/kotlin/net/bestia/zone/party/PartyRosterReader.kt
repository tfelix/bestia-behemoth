package net.bestia.zone.party

import net.bestia.zone.account.persistence.PartyRepository
import net.bestia.zone.identity.ecs.PartyExpShare
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * The stored half of a roster, as plain values, so [PartyRosterPublisher] can read the world and send after the
 * transaction has ended without anything loading lazily.
 */
@Component
class PartyRosterReader(
  private val partyRepository: PartyRepository,
) {

  data class Rows(
    val partyId: Long,
    val name: String,
    val ownerMasterId: Long,
    val expShare: PartyExpShare,
    /** The owner first, then by name. */
    val members: List<MemberRow>,
  )

  data class MemberRow(
    val accountId: AccountId,
    val masterId: Long,
    val name: String,
    /** As last persisted; a played master's live level wins. */
    val level: Int,
    /** The names the master gave its bestias, for the one it plays. */
    val bestiaNames: Map<PlayerBestiaId, String>,
  )

  @Transactional(readOnly = true)
  fun read(partyId: Long): Rows? {
    val party = partyRepository.findByIdOrNull(partyId) ?: return null

    val members = party.everyone
      .sortedWith(compareBy({ it.id != party.owner.id }, { it.name }))
      .map { master ->
        MemberRow(
          accountId = master.account.id,
          masterId = master.id,
          name = master.name,
          level = master.level,
          bestiaNames = master.bestias.ownedBestias
            .mapNotNull { bestia -> bestia.name?.let { bestia.id to it } }
            .toMap()
        )
      }

    return Rows(party.id, party.name, party.owner.id, party.expShare, members)
  }

  @Transactional(readOnly = true)
  fun partyIdOfMaster(masterId: Long): Long? {
    return partyRepository.findPartyIdOfMaster(masterId)
  }
}
