package net.bestia.model.party

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PartyRepository : JpaRepository<Party, Long> {

  /**
   * Finds the party in which the given account is a member.
   *
   * @param accountId
   * The party which contains this account.
   * @return The party or null if the account is no member in any party.
   */
  @Query("SELECT pr FROM Party pr JOIN pr.members p WHERE p.id = :accountId")
  fun findPartyByMembership(accountId: Long): Party?
}
