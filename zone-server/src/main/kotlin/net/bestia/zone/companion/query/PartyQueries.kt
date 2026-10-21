package net.bestia.zone.companion.query

import net.bestia.bus.contract.ZoneAnswer
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PartyQueries(
  private val owned: OwnedMasters,
  private val connections: ConnectionInfoService,
) {

  @Transactional(readOnly = true)
  fun party(accountId: Long, masterId: Long): ZoneAnswer.PartyInfo? {
    val master = owned.find(accountId, masterId) ?: return null
    val party = master.party ?: return ZoneAnswer.PartyInfo(null)

    val members = party.everyone.sortedBy { it.name }.map {
      ZoneAnswer.PartyInfo.Member(
        masterId = it.id,
        name = it.name,
        level = it.level,
        online = connections.activeMasterIdOf(it.account.id) == it.id,
      )
    }
    return ZoneAnswer.PartyInfo(ZoneAnswer.PartyInfo.Party(party.name, party.owner.id, members))
  }
}
